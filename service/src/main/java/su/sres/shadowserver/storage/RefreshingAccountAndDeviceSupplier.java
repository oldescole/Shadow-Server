/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.storage;

import java.util.function.Supplier;
import su.sres.shadowserver.util.Pair;

public class RefreshingAccountAndDeviceSupplier implements Supplier<Pair<Account, Device>> {

  private Account account;
  private Device device;
  private final AccountsManager accountsManager;

  public RefreshingAccountAndDeviceSupplier(Account account, long deviceId, AccountsManager accountsManager) {
    this.account = account;
    this.device = account.getDevice(deviceId)
        .orElseThrow(() -> new RefreshingAccountAndDeviceNotFoundException("Could not find device"));
    this.accountsManager = accountsManager;
  }

  @Override
  public Pair<Account, Device> get() {
    if (account.isStale()) {
      account = accountsManager.get(account.getUuid())
          .orElseThrow(() -> new RuntimeException("Could not find account"));
      device = account.getDevice(device.getId())
          .orElseThrow(() -> new RefreshingAccountAndDeviceNotFoundException("Could not find device"));
    }

    return new Pair<>(account, device);
  }
}
