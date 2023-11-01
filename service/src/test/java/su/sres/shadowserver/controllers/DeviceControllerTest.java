/*
 * Original software: Copyright 2013-2021 Signal Messenger, LLC
 * Modified software: Copyright 2019-2023 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.controllers;

import com.google.common.collect.ImmutableSet;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import su.sres.shadowserver.auth.AuthenticatedAccount;
import su.sres.shadowserver.auth.DisabledPermittedAuthenticatedAccount;
import su.sres.shadowserver.auth.StoredVerificationCode;
import su.sres.shadowserver.entities.AccountAttributes;
import su.sres.shadowserver.entities.DeviceResponse;
import su.sres.shadowserver.limits.RateLimiter;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.mappers.DeviceLimitExceededExceptionMapper;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.AccountsManager;
import su.sres.shadowserver.storage.Device;
import su.sres.shadowserver.storage.Device.DeviceCapabilities;
import su.sres.shadowserver.storage.KeysScyllaDb;
import su.sres.shadowserver.storage.MessagesManager;
import su.sres.shadowserver.storage.StoredVerificationCodeManager;
import su.sres.shadowserver.util.AccountsHelper;
import su.sres.shadowserver.util.AuthHelper;
import su.sres.shadowserver.util.VerificationCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(DropwizardExtensionsSupport.class)
class DeviceControllerTest {
  @Path("/v1/devices")
  static class DumbVerificationDeviceController extends DeviceController {
    public DumbVerificationDeviceController(StoredVerificationCodeManager pendingDevices,
        AccountsManager accounts,
        MessagesManager messages,
        KeysScyllaDb keys,
        RateLimiters rateLimiters,
        Map<String, Integer> deviceConfiguration,
        int verificationCodeLifetime) {
      super(pendingDevices, accounts, messages, keys, rateLimiters, deviceConfiguration, verificationCodeLifetime);
    }

    @Override
    protected VerificationCode generateVerificationCode() {
      return new VerificationCode(5678901);
    }
  }

  private static StoredVerificationCodeManager pendingDevicesManager = mock(StoredVerificationCodeManager.class);
  private static AccountsManager accountsManager = mock(AccountsManager.class);
  private static MessagesManager messagesManager = mock(MessagesManager.class);
  private static KeysScyllaDb keys = mock(KeysScyllaDb.class);
  private static RateLimiters rateLimiters = mock(RateLimiters.class);
  private static RateLimiter rateLimiter = mock(RateLimiter.class);
  private static Account account = mock(Account.class);
  private static Account maxedAccount = mock(Account.class);

  private static Device masterDevice = mock(Device.class);

  private static Map<String, Integer> deviceConfiguration = new HashMap<String, Integer>() {
    {
    }
  };

  private static final int VERIFICATION_CODE_LIFETIME = 48;

  private static final ResourceExtension resources = ResourceExtension.builder()
      .addProvider(AuthHelper.getAuthFilter())
      .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(AuthenticatedAccount.class, DisabledPermittedAuthenticatedAccount.class)))
      .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
      .addProvider(new DeviceLimitExceededExceptionMapper())
      .addResource(new DumbVerificationDeviceController(pendingDevicesManager,
          accountsManager,
          messagesManager,
          keys,
          rateLimiters,
          deviceConfiguration,
          VERIFICATION_CODE_LIFETIME))
      .build();

  @BeforeEach
  void setup() {
    when(rateLimiters.getSmsDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVerifyLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getAllocateDeviceLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVerifyDeviceLimiter()).thenReturn(rateLimiter);

    when(masterDevice.getId()).thenReturn(1L);

    when(account.getNextDeviceId()).thenReturn(42L);
    when(account.getUserLogin()).thenReturn(AuthHelper.VALID_NUMBER);
    when(account.getUuid()).thenReturn(AuthHelper.VALID_UUID);

    when(account.isEnabled()).thenReturn(false);
    when(account.isGroupsV2Supported()).thenReturn(true);
    when(account.isGv1MigrationSupported()).thenReturn(true);
    when(account.isSenderKeySupported()).thenReturn(true);
    when(account.isAnnouncementGroupSupported()).thenReturn(true);
    when(account.isChangeUserLoginSupported()).thenReturn(true);

    when(pendingDevicesManager.getCodeForUserLogin(AuthHelper.VALID_NUMBER)).thenReturn(Optional.of(new StoredVerificationCode("5678901", System.currentTimeMillis(), null)));
    when(pendingDevicesManager.getCodeForUserLogin(AuthHelper.VALID_NUMBER_TWO)).thenReturn(Optional.of(new StoredVerificationCode("1112223", System.currentTimeMillis() - TimeUnit.HOURS.toMillis(50), null)));
    when(accountsManager.get(AuthHelper.VALID_NUMBER)).thenReturn(Optional.of(account));
    when(accountsManager.get(AuthHelper.VALID_NUMBER_TWO)).thenReturn(Optional.of(maxedAccount));

    AccountsHelper.setupMockUpdate(accountsManager);
  }

  @AfterEach
  void teardown() {
    reset(
        pendingDevicesManager,
        accountsManager,
        messagesManager,
        keys,
        rateLimiters,
        rateLimiter,
        account,
        maxedAccount,
        masterDevice);
  }

  @Test
  void validDeviceRegisterTest() {
    VerificationCode deviceCode = resources.getJerseyTest()
        .target("/v1/devices/provisioning/code")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(VerificationCode.class);

    assertThat(deviceCode).isEqualTo(new VerificationCode(5678901));

    DeviceResponse response = resources.getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, "password1"))
        .put(Entity.entity(new AccountAttributes(false, 1234, null, true, null),
            MediaType.APPLICATION_JSON_TYPE),
            DeviceResponse.class);

    assertThat(response.getDeviceId()).isEqualTo(42L);

    verify(pendingDevicesManager).remove(AuthHelper.VALID_NUMBER);
    verify(messagesManager).clear(eq(AuthHelper.VALID_UUID), eq(42L));
  }

  @Test
  void verifyDeviceTokenBadCredentials() {
    final Response response = resources.getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", "This is not a valid authorization header")
        .put(Entity.entity(new AccountAttributes(false, 1234, null, true, null),
            MediaType.APPLICATION_JSON_TYPE));

    assertEquals(401, response.getStatus());
  }

  @Test
  void disabledDeviceRegisterTest() {
    Response response = resources.getJerseyTest()
        .target("/v1/devices/provisioning/code")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
        .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void invalidDeviceRegisterTest() {
    VerificationCode deviceCode = resources.getJerseyTest()
        .target("/v1/devices/provisioning/code")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(VerificationCode.class);

    assertThat(deviceCode).isEqualTo(new VerificationCode(5678901));

    Response response = resources.getJerseyTest()
        .target("/v1/devices/5678902")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, "password1"))
        .put(Entity.entity(new AccountAttributes(false, 1234, null, true, null),
            MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);

    verifyNoMoreInteractions(messagesManager);
  }

  @Test
  void oldDeviceRegisterTest() {
    Response response = resources.getJerseyTest()
        .target("/v1/devices/1112223")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new AccountAttributes(false, 1234, null, true, null),
            MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);

    verifyNoMoreInteractions(messagesManager);
  }

  @Test
  void maxDevicesTest() {
    Response response = resources.getJerseyTest()
        .target("/v1/devices/provisioning/code")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .get();

    assertEquals(411, response.getStatus());
    verifyNoMoreInteractions(messagesManager);
  }

  @Test
  void longNameTest() {
    Response response = resources.getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, "password1"))
        .put(Entity.entity(new AccountAttributes(false, 1234, "this is a really long name that is longer than 80 characters it's so long that it's even longer than 204 characters. that's a lot of characters. we're talking lots and lots and lots of characters. 12345678", true, null), MediaType.APPLICATION_JSON_TYPE));

    assertEquals(response.getStatus(), 422);
    verifyNoMoreInteractions(messagesManager);
  }

  @ParameterizedTest
  @MethodSource
  void deviceDowngradeCapabilitiesTest(final String userAgent, final boolean gv2, final boolean gv2_2, final boolean gv2_3, final int expectedStatus) {
    DeviceCapabilities deviceCapabilities = new DeviceCapabilities(gv2, gv2_2, gv2_3, true, false, true, true, true, true);
    AccountAttributes accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    Response response = resources.getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, "password1"))
        .header("User-Agent", userAgent)
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(expectedStatus);

    if (expectedStatus >= 300) {
      verifyNoMoreInteractions(messagesManager);
    }
  }

  private static Stream<Arguments> deviceDowngradeCapabilitiesTest() {
    return Stream.of(
//	             User-Agent                          gv2    gv2-2  gv2-3  expected
        Arguments.of("Shadow-Android/4.68.3 Android/25", false, false, false, 409),
        Arguments.of("Shadow-Android/4.68.3 Android/25", true, false, false, 409),
        Arguments.of("Shadow-Android/4.68.3 Android/25", false, true, false, 409),
        Arguments.of("Shadow-Android/4.68.3 Android/25", false, false, true, 200),
        Arguments.of("Shadow-iOS/3.9.0", false, false, false, 409),
        Arguments.of("Shadow-iOS/3.9.0", true, false, false, 409),
        Arguments.of("Shadow-iOS/3.9.0", false, true, false, 200),
        Arguments.of("Shadow-iOS/3.9.0", false, false, true, 200),
        Arguments.of("Shadow-Desktop/1.32.0-beta.3", false, false, false, 409),
        Arguments.of("Shadow-Desktop/1.32.0-beta.3", true, false, false, 409),
        Arguments.of("Shadow-Desktop/1.32.0-beta.3", false, true, false, 409),
        Arguments.of("Shadow-Desktop/1.32.0-beta.3", false, false, true, 200),
        Arguments.of("Old client with unparsable UA", false, false, false, 409),
        Arguments.of("Old client with unparsable UA", true, false, false, 409),
        Arguments.of("Old client with unparsable UA", false, true, false, 409),
        Arguments.of("Old client with unparsable UA", false, false, true, 409));
  }

  @Test
  void deviceDowngradeGv1MigrationTest() {
    DeviceCapabilities deviceCapabilities = new DeviceCapabilities(true, true, true, true, false, false, true, true, true);
    AccountAttributes accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    Response response = resources.getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, "password1"))
        .header("user-agent", "Shadow-Android/4.68.3 Android/25")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(409);

    deviceCapabilities = new DeviceCapabilities(true, true, true, true, false, true, true, true, true);
    accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    response = resources.getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, "password1"))
        .header("user-agent", "Shadow-Android/4.68.3 Android/25")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);

  }

  @Test
  void deviceDowngradeSenderKeyTest() {
    DeviceCapabilities deviceCapabilities = new DeviceCapabilities(true, true, true, true, true, true, false, true, true);
    AccountAttributes accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    Response response = resources
        .getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Shadow-Android/5.42.8675309 Android/30")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus()).isEqualTo(409);

    deviceCapabilities = new DeviceCapabilities(true, true, true, true, true, true, true, true, true);
    accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    response = resources
        .getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Shadow-Android/5.42.8675309 Android/30")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void deviceDowngradeAnnouncementGroupTest() {
    DeviceCapabilities deviceCapabilities = new DeviceCapabilities(true, true, true, true, true, true, true, false, true);
    AccountAttributes accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    Response response = resources
        .getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Shadow-Android/5.42.8675309 Android/30")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus()).isEqualTo(409);

    deviceCapabilities = new DeviceCapabilities(true, true, true, true, true, true, true, true, true);
    accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    response = resources
        .getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Signal-Android/5.42.8675309 Android/30")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void deviceDowngradeChangeUserLoginTest() {
    DeviceCapabilities deviceCapabilities = new DeviceCapabilities(true, true, true, true, true, true, true, true, false);
    AccountAttributes accountAttributes =
        new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    Response response = resources
        .getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Signal-Android/5.42.8675309 Android/30")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus()).isEqualTo(409);

    deviceCapabilities = new DeviceCapabilities(true, true, true, true, true, true, true, true, true);
    accountAttributes = new AccountAttributes(false, 1234, null, true, deviceCapabilities);
    response = resources
        .getJerseyTest()
        .target("/v1/devices/5678901")
        .request()
        .header("Authorization", AuthHelper.getProvisioningAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Shadow-Android/5.42.8675309 Android/30")
        .put(Entity.entity(accountAttributes, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus()).isEqualTo(200);

  }

  @Test
  void deviceRemovalClearsMessagesAndKeys() {

    // this is a static mock, so it might have previous invocations
    clearInvocations(AuthHelper.VALID_ACCOUNT);

    final long deviceId = 2;

    final Response response = resources
        .getJerseyTest()
        .target("/v1/devices/" + deviceId)
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Shadow-Android/5.42.8675309 Android/30")
        .delete();

    assertThat(response.getStatus()).isEqualTo(204);

    verify(messagesManager, times(2)).clear(AuthHelper.VALID_UUID, deviceId);
    verify(accountsManager, times(1)).update(eq(AuthHelper.VALID_ACCOUNT), any());
    verify(AuthHelper.VALID_ACCOUNT).removeDevice(deviceId);

    verify(keys).delete(AuthHelper.VALID_UUID, deviceId);
  }
}
