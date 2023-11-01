/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.limits;

import su.sres.shadowserver.storage.Account;
import java.time.Duration;

public class RateLimitChallengeException extends Exception {

  private final Account account;
  private final Duration retryAfter;

  public RateLimitChallengeException(final Account account, final Duration retryAfter) {
    this.account = account;
    this.retryAfter = retryAfter;
  }

  public Account getAccount() {
    return account;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }
}
