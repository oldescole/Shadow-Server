/*
 * Original software: Copyright 2013-2021 Signal Messenger, LLC
 * Modified software: Copyright 2019-2023 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.auth;

import java.util.Optional;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import io.micrometer.core.instrument.Metrics;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.AccountsManager;

import static com.codahale.metrics.MetricRegistry.name;

public class AccountAuthenticator extends BaseAccountAuthenticator implements
Authenticator<BasicCredentials, AuthenticatedAccount> {
  
  private static final String AUTHENTICATION_COUNTER_NAME = name(AccountAuthenticator.class, "authenticate");

  public AccountAuthenticator(AccountsManager accountsManager) {
    super(accountsManager);
  }

  @Override
  public Optional<AuthenticatedAccount> authenticate(BasicCredentials basicCredentials) {
    final Optional<AuthenticatedAccount> maybeAuthenticatedAccount = super.authenticate(basicCredentials, true);

    // TODO Remove after announcement groups have launched
    maybeAuthenticatedAccount.ifPresent(authenticatedAccount ->
        Metrics.counter(AUTHENTICATION_COUNTER_NAME,
            "supportsAnnouncementGroups",
            String.valueOf(authenticatedAccount.getAccount().isAnnouncementGroupSupported()))
            .increment());

    return maybeAuthenticatedAccount;
  }
}