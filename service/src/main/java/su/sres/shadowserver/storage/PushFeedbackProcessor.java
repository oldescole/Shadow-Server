/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.storage;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import su.sres.shadowserver.util.Constants;
import su.sres.shadowserver.util.Util;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

public class PushFeedbackProcessor extends AccountDatabaseCrawlerListener {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter expired = metricRegistry.meter(name(getClass(), "unregistered", "expired"));
  private final Meter recovered = metricRegistry.meter(name(getClass(), "unregistered", "recovered"));

  private final AccountsManager accountsManager;

  public PushFeedbackProcessor(AccountsManager accountsManager) {
    this.accountsManager = accountsManager;
  }

  @Override
  public void onCrawlStart() {
  }

  @Override
  public void onCrawlEnd(Optional<UUID> toUuid) {
  }

  @Override
  protected void onCrawlChunk(Optional<UUID> fromUuid, List<Account> chunkAccounts) {
    for (Account account : chunkAccounts) {
      boolean update = false;

      final Set<Device> devices = account.getDevices();
      for (Device device : devices) {
        if (deviceNeedsUpdate(device)) {
          if (deviceExpired(device)) {
            if (device.isEnabled()) {
              expired.mark();
              update = true;
            }
          } else {
            recovered.mark();
            update = true;
          }
        }
      }

      if (update) {
        // fetch a new version, since the chunk is shared and implicitly read-only
        accountsManager.get(account.getUuid()).ifPresent(accountToUpdate -> {
          accountsManager.update(accountToUpdate, a -> {
            for (Device device : a.getDevices()) {
              if (deviceNeedsUpdate(device)) {
                if (deviceExpired(device)) {
                  if (!Util.isEmpty(device.getApnId())) {
                    if (device.getId() == 1) {
                      device.setUserAgent("OWI");
                    } else {
                      device.setUserAgent("OWP");
                    }
                  } else if (!Util.isEmpty(device.getGcmId())) {
                    device.setUserAgent("OWA");
                  }
                  device.setGcmId(null);
                  device.setApnId(null);
                  device.setVoipApnId(null);
                  device.setFetchesMessages(false);
                } else {
                  device.setUninstalledFeedbackTimestamp(0);
                }
              }
            }
          });
        });
      }
    }
  }

  private boolean deviceNeedsUpdate(final Device device) {
    return device.getUninstalledFeedbackTimestamp() != 0 &&
        device.getUninstalledFeedbackTimestamp() + TimeUnit.DAYS.toMillis(2) <= Util.todayInMillis();
  }

  private boolean deviceExpired(final Device device) {
    return device.getLastSeen() + TimeUnit.DAYS.toMillis(2) <= Util.todayInMillis();
  }
}