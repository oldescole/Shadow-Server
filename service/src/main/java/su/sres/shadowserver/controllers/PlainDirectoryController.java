/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.controllers;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import su.sres.shadowserver.auth.AuthenticatedAccount;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.AccountsManager;
import su.sres.shadowserver.storage.DirectoryManager;
import su.sres.shadowserver.storage.protos.DirectoryResponse;
import su.sres.shadowserver.storage.protos.DirectoryUpdate;
import su.sres.shadowserver.storage.protos.DirectoryUpdate.Type;

import static su.sres.shadowserver.storage.DirectoryManager.INCREMENTAL_UPDATES_TO_HOLD;

@Path("/v1/dirplain")
public class PlainDirectoryController {

  private final Logger logger = LoggerFactory.getLogger(PlainDirectoryController.class);
//  private final MetricRegistry metricRegistry    = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
//  private final Histogram      contactsHistogram = metricRegistry.histogram(name(getClass(), "contacts"));

  private final RateLimiters rateLimiters;
  private final DirectoryManager directory;
  private final AccountsManager accountsManager;
  private final AtomicInteger directoryReadLock;

  public PlainDirectoryController(RateLimiters rateLimiters, AccountsManager accountsManager) {
    this.accountsManager = accountsManager;
    this.rateLimiters = rateLimiters;

    directory = accountsManager.getDirectoryManager();
    directoryReadLock = new AtomicInteger(0);
  }

  @Timed
  @GET
  @Path("/download/{version}")
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public DirectoryResponse downloadDirectory(@PathParam("version") String receivedVersion, @Auth AuthenticatedAccount auth) throws RateLimitExceededException {
    rateLimiters.getDirectoryLimiter().validate(auth.getAccount().getUuid());

    long remoteVersion = Long.parseLong(receivedVersion);
    long localVersion = accountsManager.getDirectoryVersion();

    if (
    // if directory is write locked, return no-update, whatever the version
    accountsManager.getAccountCreationLock() ||
        accountsManager.getAccountRemovalLock() ||
        accountsManager.getDirectoryRestoreLock() ||
        // if the local version is same as remote, return no-update as well
        remoteVersion == localVersion) {

      return noUpdateResponse(localVersion);
    }

    if (remoteVersion > localVersion) {

      // this should not happen except when something is going seriously wrong
      throw new WebApplicationException(500);
    }

    directoryReadLock.getAndIncrement();
    directory.setDirectoryReadLock();

    try {

      if (remoteVersion == 0) {

        return fullDirectoryResponse(localVersion);

      } else {

        long versionDiff = localVersion - remoteVersion;

        if (versionDiff > INCREMENTAL_UPDATES_TO_HOLD) {

          return fullDirectoryResponse(localVersion);

        } else {

          HashMap<String, String> incrementalUpdate = directory.retrieveIncrementalUpdate((int) versionDiff);

          if (!incrementalUpdate.isEmpty()) {

            return DirectoryResponse.newBuilder()
                .setVersion(localVersion)
                .setDirectoryUpdate(buildIncrementalUpdate(incrementalUpdate))
                .build();
          } else {

            return fullDirectoryResponse(localVersion);
          }
        }

      }
    } finally {

      if (directoryReadLock.decrementAndGet() == 0)
        directory.releaseDirectoryReadLock();
    }
  }

  @Timed
  @GET
  @Path("/download/forcefull")
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public DirectoryResponse downloadFullDirectory(@Auth AuthenticatedAccount auth) throws RateLimitExceededException {
    rateLimiters.getDirectoryLimiter().validate(auth.getAccount().getUuid());

    long localVersion = accountsManager.getDirectoryVersion();

    if (
    // if directory is write locked, return no-update, whatever the version
    accountsManager.getAccountCreationLock() ||
        accountsManager.getAccountRemovalLock() ||
        accountsManager.getDirectoryRestoreLock()) {

      return noUpdateResponse(localVersion);
    }

    directoryReadLock.getAndIncrement();
    directory.setDirectoryReadLock();

    try {
      return fullDirectoryResponse(localVersion);
    } finally {
      if (directoryReadLock.decrementAndGet() == 0)
        directory.releaseDirectoryReadLock();
    }
  }

  private DirectoryResponse fullDirectoryResponse(long version) {

    if (!accountsManager.getDirectoryRestoreLock()) {

      return DirectoryResponse.newBuilder()
          .setVersion(version)
          .setDirectoryUpdate(getFullDirectory())
          .build();
    } else {

      // if directory restore is currently in progress, we simply return no-update in
      // order to avoid a possible race condition
      return noUpdateResponse(version);
    }
  }

  private DirectoryResponse noUpdateResponse(long version) {

    return DirectoryResponse.newBuilder()
        .setVersion(version)
        .setIsUpdate(false)
        .build();
  }

  private DirectoryUpdate getFullDirectory() {

    HashMap<String, String> retrievedPlainDirectory = directory.retrievePlainDirectory();

    if (!retrievedPlainDirectory.isEmpty()) {

      return DirectoryUpdate.newBuilder()
          .setType(Type.FULL)
          .putAllDirectoryEntry(retrievedPlainDirectory)
          .build();

    } else {

      // plain directory should never be empty; if it's not the case then something is
      // wrong with Redis and we need to recreate it from SQL

      // getFullDirectory() should not be invoked while the directory restoration lock
      // is set, so there should be no race condition here
      accountsManager.restorePlainDirectory();

      return DirectoryUpdate.newBuilder()
          .setType(Type.FULL)
          .putAllDirectoryEntry(directory.retrievePlainDirectory())
          .build();
    }
  }

  private DirectoryUpdate buildIncrementalUpdate(HashMap<String, String> incrementalUpdate) {

    return DirectoryUpdate.newBuilder()
        .setType(Type.INCREMENTAL)
        .putAllDirectoryEntry(incrementalUpdate)
        .build();
  }
}
