/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.storage;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.sres.shadowserver.util.SystemMapper;
import su.sres.shadowserver.storage.mappers.AccountRowMapper;
import su.sres.shadowserver.util.Constants;

import org.jdbi.v3.core.transaction.TransactionIsolationLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;

public class Accounts implements AccountStore {

  private final Logger logger = LoggerFactory.getLogger(Accounts.class);

  public static final String ID = "id";
  public static final String UID = "uuid";
  public static final String USER_LOGIN = "number";
  public static final String DATA = "data";
  public static final String VERSION = "version";
  public static final String DIR_VER = "directory_version";
  public static final String PAR = "parameter";
  public static final String PAR_VAL = "parameter_value";

  private static final ObjectMapper mapper = SystemMapper.getMapper();

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Timer createTimer = metricRegistry.timer(name(Accounts.class, "create"));
  private final Timer updateTimer = metricRegistry.timer(name(Accounts.class, "update"));
  private final Timer getByUserLoginTimer = metricRegistry.timer(name(Accounts.class, "getByUserLogin"));
  private final Timer getByUuidTimer = metricRegistry.timer(name(Accounts.class, "getByUuid"));
  private final Timer getAllFromTimer = metricRegistry.timer(name(Accounts.class, "getAllFrom"));
  private final Timer getAllFromOffsetTimer = metricRegistry.timer(name(Accounts.class, "getAllFromOffset"));
  private final Timer deleteTimer = metricRegistry.timer(name(Accounts.class, "delete"));
  private final Timer vacuumTimer = metricRegistry.timer(name(Accounts.class, "vacuum"));

  // for DirectoryUpdater and directory restore
  private final Timer getAllTimer = metricRegistry.timer(name(Accounts.class, "getAll"));

  private final FaultTolerantDatabase database;

  public Accounts(FaultTolerantDatabase database) {
    this.database = database;
    this.database.getDatabase().registerRowMapper(new AccountRowMapper());
  }

  @Override
  public boolean create(Account account, long directoryVersion) {
    return database.with(jdbi -> jdbi.inTransaction(TransactionIsolationLevel.SERIALIZABLE, handle -> {
      try (Timer.Context ignored = createTimer.time()) {

        // insert the account into the database and return the uuid; if the number
        // already exists, just update data; ultimately if the "old" uuid differs means
        // that the account is not new, and the new random uuid is reset to the old one

        // TODO: if directory holds more than just usernames in future we shall need to
        // update the directory version as well.
        final Map<String, Object> resultMap = handle.createQuery("INSERT INTO accounts (" + USER_LOGIN + ", " + UID + ", " + DATA + ", " + DIR_VER + ") VALUES (:number, :uuid, CAST(:data AS json), :directory_version) ON CONFLICT(number) DO UPDATE SET data = EXCLUDED.data, " + VERSION + " = accounts.version + 1 RETURNING uuid, version")
            .bind("number", account.getUserLogin())
            .bind("uuid", account.getUuid())
            .bind("data", mapper.writeValueAsString(account))
            .bind("directory_version", (directoryVersion))
            .mapToMap()
            .findOnly();

        handle.createUpdate("UPDATE miscellaneous SET " + PAR_VAL + " = :directory_version WHERE " + PAR + " = '" + DIR_VER + "'")
            .bind("directory_version", directoryVersion)
            .execute();

        final UUID uuid = (UUID) resultMap.get(UID);
        final int version = (int) resultMap.get(VERSION);

        boolean isNew;
        isNew = uuid.equals(account.getUuid());

        account.setUuid(uuid);
        account.setVersion(version);
        return isNew;

      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
    }));
  }

  @Override
  public void update(Account account) throws ContestedOptimisticLockException {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = updateTimer.time()) {
        final int newVersion = account.getVersion() + 1;
        int rowsModified = handle.createUpdate("UPDATE accounts SET " + DATA + " = CAST(:data AS json), " + VERSION + " = :newVersion WHERE " + UID + " = :uuid AND " + VERSION + " = :version")
            .bind("uuid", account.getUuid())
            .bind("data", mapper.writeValueAsString(account))
            .bind("version", account.getVersion())
            .bind("newVersion", newVersion)
            .execute();

        if (rowsModified == 0) {
          throw new ContestedOptimisticLockException();
        }

        account.setVersion(newVersion);

      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
    }));
  }

  /*
   * public void update(Account account, boolean isRemoval, long directoryVersion)
   * { database.use(jdbi -> jdbi.useHandle(handle -> { try (Timer.Context ignored
   * = updateTimer.time()) { handle.createUpdate("UPDATE accounts SET " + DATA +
   * " = CAST(:data AS json) WHERE " + UID + " = :uuid") .bind("uuid",
   * account.getUuid()) .bind("data", mapper.writeValueAsString(account))
   * .execute();
   * 
   * // TODO: currently we increment directory version only on removal; if
   * directory // holds more than just usernames in future, will need to increment
   * on any // update
   * 
   * // always false in the current setup, can be removed // if (isRemoval) { //
   * handle.createUpdate("UPDATE accounts SET " + DIR_VER +
   * " = :directory_version WHERE " + UID + " = :uuid") //
   * .bind("directory_version", directoryVersion) // .bind("uuid",
   * account.getUuid()) // .execute(); // }
   * 
   * } catch (JsonProcessingException e) { throw new IllegalArgumentException(e);
   * } })); }
   */

  @Override
  public Optional<Account> get(String userLogin) {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getByUserLoginTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts WHERE " + USER_LOGIN + " = :number")
            .bind("number", userLogin)
            .mapTo(Account.class)
            .findFirst();
      }
    }));
  }

  @Override
  public Optional<Account> get(UUID uuid) {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getByUuidTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts WHERE " + UID + " = :uuid")
            .bind("uuid", uuid)
            .mapTo(Account.class)
            .findFirst();
      }
    }));
  }

  public AccountCrawlChunk getAllFrom(UUID from, int length) {
    final List<Account> accounts = database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Context ignored = getAllFromOffsetTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts WHERE " + UID + " > :from ORDER BY " + UID + " LIMIT :limit")
            .bind("from", from)
            .bind("limit", length)
            .mapTo(Account.class)
            .list();
      }
    }));
    return buildChunkForAccounts(accounts);
  }

  public AccountCrawlChunk getAllFrom(int length) {
    final List<Account> accounts = database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getAllFromTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts ORDER BY " + UID + " LIMIT :limit")
            .bind("limit", length)
            .mapTo(Account.class)
            .list();
      }
    }));

    return buildChunkForAccounts(accounts);
  }

  // this is used by directory restore and DirectoryUpdater
  public List<Account> getAll(int offset, int length) {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getAllTimer.time()) {

        return handle.createQuery("SELECT * FROM accounts OFFSET :offset LIMIT :limit").bind("offset", offset)
            .bind("limit", length)
            .mapTo(Account.class)
            .list();
      }
    }));
  }

  private AccountCrawlChunk buildChunkForAccounts(final List<Account> accounts) {
    return new AccountCrawlChunk(accounts, accounts.isEmpty() ? null : accounts.get(accounts.size() - 1).getUuid());
  }

  @Override
  public void delete(final UUID uuid, long directoryVersion) {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = deleteTimer.time()) {
        handle.createUpdate("DELETE FROM accounts WHERE " + UID + " = :uuid")
            .bind("uuid", uuid)
            .execute();

        handle.createUpdate("UPDATE miscellaneous SET " + PAR_VAL + " = :directory_version WHERE " + PAR + " = '" + DIR_VER + "'")
            .bind("directory_version", directoryVersion)
            .execute();
      }
    }));
  }

  public void vacuum() {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = vacuumTimer.time()) {
        handle.execute("VACUUM accounts");
      }
    }));
  }

  // TODO: migrate to Scylla via AccountsScyllaDb
  public Long restoreDirectoryVersion() {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      return handle.createQuery("SELECT " + PAR_VAL + " FROM miscellaneous WHERE " + PAR + " = '" + DIR_VER + "'")
          .mapTo(Long.class)
          .findOnly();
    }));
  }
}
