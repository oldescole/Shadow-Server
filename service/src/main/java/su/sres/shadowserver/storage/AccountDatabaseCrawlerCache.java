/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.storage;

import su.sres.shadowserver.redis.ClusterLuaScript;
import su.sres.shadowserver.redis.FaultTolerantRedisCluster;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class AccountDatabaseCrawlerCache {

  private static final String ACTIVE_WORKER_KEY = "account_database_crawler_cache_active_worker";
  private static final String LAST_UUID_KEY = "account_database_crawler_cache_last_uuid";
  private static final String ACCELERATE_KEY = "account_database_crawler_cache_accelerate";

  private static final String LAST_UUID_SCYLLA_KEY = "account_database_crawler_cache_last_uuid_scylla";

  private static final long LAST_NUMBER_TTL_MS = 86400_000L;

  private final FaultTolerantRedisCluster cacheCluster;
  private final ClusterLuaScript unlockClusterScript;
  
  private String prefix = "";

  public AccountDatabaseCrawlerCache(FaultTolerantRedisCluster cacheCluster) throws IOException {
    this.cacheCluster = cacheCluster;
    this.unlockClusterScript = ClusterLuaScript.fromResource(cacheCluster, "lua/account_database_crawler/unlock.lua", ScriptOutputType.INTEGER);
  }

  public void setAccelerated(final boolean accelerated) {
    if (accelerated) {
      cacheCluster.useCluster(connection -> connection.sync().set(getPrefixedKey(ACCELERATE_KEY), "1"));
    } else {
      cacheCluster.useCluster(connection -> connection.sync().del(getPrefixedKey(ACCELERATE_KEY)));
    }
  }

  public boolean isAccelerated() {
    return "1".equals(cacheCluster.withCluster(connection -> connection.sync().get(ACCELERATE_KEY)));
  }

  public boolean claimActiveWork(String workerId, long ttlMs) {
    return "OK".equals(cacheCluster.withCluster(connection -> connection.sync()
        .set(getPrefixedKey(ACTIVE_WORKER_KEY), workerId, SetArgs.Builder.nx().px(ttlMs))));
  }

  public void releaseActiveWork(String workerId) {
    unlockClusterScript.execute(List.of(getPrefixedKey(ACTIVE_WORKER_KEY)), List.of(workerId));
  }

  public Optional<UUID> getLastUuid() {
    final String lastUuidString = cacheCluster.withCluster(
        connection -> connection.sync().get(getPrefixedKey(LAST_UUID_KEY)));

    if (lastUuidString == null)
      return Optional.empty();
    else
      return Optional.of(UUID.fromString(lastUuidString));
  }

  public void setLastUuid(Optional<UUID> lastUuid) {
    if (lastUuid.isPresent()) {
      cacheCluster.useCluster(connection -> connection.sync()
          .psetex(getPrefixedKey(LAST_UUID_KEY), LAST_NUMBER_TTL_MS, lastUuid.get().toString()));
    } else {
      cacheCluster.useCluster(connection -> connection.sync().del(getPrefixedKey(LAST_UUID_KEY)));
    }
  }

  public Optional<UUID> getLastUuidScylla() {
    final String lastUuidString = cacheCluster.withCluster(
        connection -> connection.sync().get(getPrefixedKey(LAST_UUID_SCYLLA_KEY)));

    if (lastUuidString == null)
      return Optional.empty();
    else
      return Optional.of(UUID.fromString(lastUuidString));
  }

  public void setLastUuidScylla(Optional<UUID> lastUuid) {
    if (lastUuid.isPresent()) {
      cacheCluster.useCluster(
          connection -> connection.sync()
              .psetex(getPrefixedKey(LAST_UUID_SCYLLA_KEY), LAST_NUMBER_TTL_MS, lastUuid.get().toString()));
    } else {
      cacheCluster.useCluster(connection -> connection.sync().del(getPrefixedKey(LAST_UUID_SCYLLA_KEY)));
    }
  }
  
  private String getPrefixedKey(final String key) {
    return prefix + key;
  }

  /**
   * Set a cache key prefix, allowing for uses beyond the canonical crawler
   */
  public void setPrefix(final String prefix) {
    this.prefix = prefix + "::";
  }
}