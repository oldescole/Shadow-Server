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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import su.sres.shadowserver.auth.AuthenticatedAccount;
import su.sres.shadowserver.auth.DisabledPermittedAuthenticatedAccount;
import su.sres.shadowserver.auth.OptionalAccess;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import su.sres.shadowserver.entities.PreKey;
import su.sres.shadowserver.entities.PreKeyCount;
import su.sres.shadowserver.entities.PreKeyResponse;
import su.sres.shadowserver.entities.PreKeyState;
import su.sres.shadowserver.entities.RateLimitChallenge;
import su.sres.shadowserver.entities.SignedPreKey;
import su.sres.shadowserver.limits.PreKeyRateLimiter;
import su.sres.shadowserver.limits.RateLimitChallengeManager;
import su.sres.shadowserver.limits.RateLimiter;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.mappers.RateLimitChallengeExceptionMapper;
import su.sres.shadowserver.mappers.ServerRejectedExceptionMapper;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.AccountsManager;
import su.sres.shadowserver.storage.Device;
import su.sres.shadowserver.storage.KeysScyllaDb;
import su.sres.shadowserver.util.AccountsHelper;
import su.sres.shadowserver.util.AuthHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static su.sres.shadowserver.util.AccountsHelper.eqUuid;

@ExtendWith(DropwizardExtensionsSupport.class)
class KeysControllerTest {

  private static final String EXISTS_NUMBER = "+14152222222";
  private static final UUID EXISTS_UUID = UUID.randomUUID();

  private static final String NOT_EXISTS_NUMBER = "+14152222220";
  private static final UUID   NOT_EXISTS_UUID   = UUID.randomUUID();

  private static final int SAMPLE_REGISTRATION_ID  =  999;
  private static final int SAMPLE_REGISTRATION_ID2 = 1002;
  private static final int SAMPLE_REGISTRATION_ID4 = 1555;

  private final PreKey SAMPLE_KEY = new PreKey(1234, "test1");
  private final PreKey SAMPLE_KEY2 = new PreKey(5667, "test3");
  private final PreKey SAMPLE_KEY3 = new PreKey(334, "test5");
  private final PreKey SAMPLE_KEY4 = new PreKey(336, "test6");

  private final SignedPreKey SAMPLE_SIGNED_KEY = new SignedPreKey(1111, "foofoo", "sig11");
  private final SignedPreKey SAMPLE_SIGNED_KEY2 = new SignedPreKey(2222, "foobar", "sig22");
  private final SignedPreKey SAMPLE_SIGNED_KEY3 = new SignedPreKey(3333, "barfoo", "sig33");
  private final SignedPreKey VALID_DEVICE_SIGNED_KEY = new SignedPreKey(89898, "zoofarb", "sigvalid");

  private static final KeysScyllaDb keysScyllaDb = mock(KeysScyllaDb.class);
  private static final AccountsManager accounts = mock(AccountsManager.class);
  private final static PreKeyRateLimiter           preKeyRateLimiter           = mock(PreKeyRateLimiter.class          );
  private final static RateLimitChallengeManager   rateLimitChallengeManager   = mock(RateLimitChallengeManager.class  );
  private final Account existsAccount = mock(Account.class);
    
  private static final RateLimiters rateLimiters = mock(RateLimiters.class);
  private static final RateLimiter rateLimiter = mock(RateLimiter.class);

  private static final ResourceExtension resources = ResourceExtension.builder()
      .addProvider(AuthHelper.getAuthFilter())
      .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(
          ImmutableSet.of(AuthenticatedAccount.class, DisabledPermittedAuthenticatedAccount.class)))
      .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
      .addResource(new RateLimitChallengeExceptionMapper(rateLimitChallengeManager))
      .addResource(new ServerRejectedExceptionMapper())
      .addResource(
          new KeysController(rateLimiters, keysScyllaDb, accounts, preKeyRateLimiter, rateLimitChallengeManager))
      .build();



  @BeforeEach
  void setup() {
    final Device sampleDevice = mock(Device.class);
    final Device sampleDevice2 = mock(Device.class);
    final Device sampleDevice3 = mock(Device.class);
    final Device sampleDevice4 = mock(Device.class);

    Set<Device> allDevices = new HashSet<>() {
      {
        add(sampleDevice);
        add(sampleDevice2);
        add(sampleDevice3);
        add(sampleDevice4);
      }
    };
    
    AccountsHelper.setupMockUpdate(accounts);

    when(sampleDevice.getRegistrationId()).thenReturn(SAMPLE_REGISTRATION_ID);
    when(sampleDevice2.getRegistrationId()).thenReturn(SAMPLE_REGISTRATION_ID2);
    when(sampleDevice3.getRegistrationId()).thenReturn(SAMPLE_REGISTRATION_ID2);
    when(sampleDevice4.getRegistrationId()).thenReturn(SAMPLE_REGISTRATION_ID4);
    when(sampleDevice.isEnabled()).thenReturn(true);
    when(sampleDevice2.isEnabled()).thenReturn(true);
    when(sampleDevice3.isEnabled()).thenReturn(false);
    when(sampleDevice4.isEnabled()).thenReturn(true);
    when(sampleDevice.getSignedPreKey()).thenReturn(SAMPLE_SIGNED_KEY);
    when(sampleDevice2.getSignedPreKey()).thenReturn(SAMPLE_SIGNED_KEY2);
    when(sampleDevice3.getSignedPreKey()).thenReturn(SAMPLE_SIGNED_KEY3);
    when(sampleDevice4.getSignedPreKey()).thenReturn(null);
    when(sampleDevice.getId()).thenReturn(1L);
    when(sampleDevice2.getId()).thenReturn(2L);
    when(sampleDevice3.getId()).thenReturn(3L);
    when(sampleDevice4.getId()).thenReturn(4L);

    when(existsAccount.getDevice(1L)).thenReturn(Optional.of(sampleDevice));
    when(existsAccount.getDevice(2L)).thenReturn(Optional.of(sampleDevice2));
    when(existsAccount.getDevice(3L)).thenReturn(Optional.of(sampleDevice3));
    when(existsAccount.getDevice(4L)).thenReturn(Optional.of(sampleDevice4));
    when(existsAccount.getDevice(22L)).thenReturn(Optional.empty());
    when(existsAccount.getDevices()).thenReturn(allDevices);
    when(existsAccount.isEnabled()).thenReturn(true);
    when(existsAccount.getIdentityKey()).thenReturn("existsidentitykey");
    when(existsAccount.getUserLogin()).thenReturn(EXISTS_NUMBER);
    when(existsAccount.getUnidentifiedAccessKey()).thenReturn(Optional.of("1337".getBytes()));

    when(accounts.get(EXISTS_NUMBER)).thenReturn(Optional.of(existsAccount));
    when(accounts.get(EXISTS_UUID)).thenReturn(Optional.of(existsAccount));
    
    when(accounts.get(NOT_EXISTS_NUMBER)).thenReturn(Optional.empty());
    when(accounts.get(NOT_EXISTS_UUID)).thenReturn(Optional.empty());
    
    when(rateLimiters.getPreKeysLimiter()).thenReturn(rateLimiter);

    when(keysScyllaDb.take(eq(existsAccount), eq(1L))).thenReturn(Optional.of(SAMPLE_KEY));

    when(keysScyllaDb.take(existsAccount)).thenReturn(Map.of(1L, SAMPLE_KEY,
        2L, SAMPLE_KEY2,
        3L, SAMPLE_KEY3,
        4L, SAMPLE_KEY4));

    when(keysScyllaDb.getCount(eq(AuthHelper.VALID_ACCOUNT), eq(1L))).thenReturn(5);

    when(AuthHelper.VALID_DEVICE.getSignedPreKey()).thenReturn(VALID_DEVICE_SIGNED_KEY);
    when(AuthHelper.VALID_ACCOUNT.getIdentityKey()).thenReturn(null);
  }
  
  @AfterEach
  void teardown() {
    reset(
        keysScyllaDb,
        accounts,       
        preKeyRateLimiter,
        existsAccount,
        rateLimiters,
        rateLimiter,        
        rateLimitChallengeManager
    );
  }


  @Test
  void validKeyStatusTestByNumberV2() {
    PreKeyCount result = resources.getJerseyTest().target("/v2/keys").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(PreKeyCount.class);

    assertThat(result.getCount()).isEqualTo(4);

    verify(keysScyllaDb).getCount(eq(AuthHelper.VALID_ACCOUNT), eq(1L));
  }  

  @Test
  void getSignedPreKeyV2ByUuid() {
    SignedPreKey result = resources.getJerseyTest().target("/v2/keys/signed").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(SignedPreKey.class);

    assertThat(result.getSignature()).isEqualTo(VALID_DEVICE_SIGNED_KEY.getSignature());
    assertThat(result.getKeyId()).isEqualTo(VALID_DEVICE_SIGNED_KEY.getKeyId());
    assertThat(result.getPublicKey()).isEqualTo(VALID_DEVICE_SIGNED_KEY.getPublicKey());
  }

  @Test
  void putSignedPreKeyV2() {
    SignedPreKey test = new SignedPreKey(9998, "fooozzz", "baaarzzz");
    Response response = resources.getJerseyTest().target("/v2/keys/signed").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.entity(test, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.VALID_DEVICE).setSignedPreKey(eq(test));
    verify(accounts).updateDevice(eq(AuthHelper.VALID_ACCOUNT), anyLong(), any());
  }

  @Test
  void disabledPutSignedPreKeyV2() {
    SignedPreKey test = new SignedPreKey(9999, "fooozzz", "baaarzzz");
    Response response = resources.getJerseyTest().target("/v2/keys/signed").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
        .put(Entity.entity(test, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void validSingleRequestTestV2() {
    PreKeyResponse result = resources.getJerseyTest().target(String.format("/v2/keys/%s/1", EXISTS_UUID)).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(PreKeyResponse.class);

    assertThat(result.getIdentityKey()).isEqualTo(existsAccount.getIdentityKey());
    assertThat(result.getDevicesCount()).isEqualTo(1);
    assertThat(result.getDevice(1).getPreKey().getKeyId()).isEqualTo(SAMPLE_KEY.getKeyId());
    assertThat(result.getDevice(1).getPreKey().getPublicKey()).isEqualTo(SAMPLE_KEY.getPublicKey());
    assertThat(result.getDevice(1).getSignedPreKey()).isEqualTo(existsAccount.getDevice(1).get().getSignedPreKey());

    verify(keysScyllaDb).take(eq(existsAccount), eq(1L));
    verifyNoMoreInteractions(keysScyllaDb);
  }

  @Test
  void testUnidentifiedRequest() {
    PreKeyResponse result = resources.getJerseyTest().target(String.format("/v2/keys/%s/1", EXISTS_UUID))
        .request()
        .header(OptionalAccess.UNIDENTIFIED, AuthHelper.getUnidentifiedAccessHeader("1337".getBytes()))
        .get(PreKeyResponse.class);

    assertThat(result.getIdentityKey()).isEqualTo(existsAccount.getIdentityKey());
    assertThat(result.getDevicesCount()).isEqualTo(1);
    assertThat(result.getDevice(1).getPreKey().getKeyId()).isEqualTo(SAMPLE_KEY.getKeyId());
    assertThat(result.getDevice(1).getPreKey().getPublicKey()).isEqualTo(SAMPLE_KEY.getPublicKey());
    assertThat(result.getDevice(1).getSignedPreKey()).isEqualTo(existsAccount.getDevice(1).get().getSignedPreKey());

    verify(keysScyllaDb).take(eq(existsAccount), eq(1L));
    verifyNoMoreInteractions(keysScyllaDb);
  }
  
  @Test
  void testNoDevices() {

    when(existsAccount.getDevices()).thenReturn(Collections.emptySet());

    Response result = resources.getJerseyTest()
        .target(String.format("/v2/keys/%s/*", EXISTS_UUID))
        .request()
        .header(OptionalAccess.UNIDENTIFIED, AuthHelper.getUnidentifiedAccessHeader("1337".getBytes()))
        .get();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(404);
  }

  @Test
  void testUnauthorizedUnidentifiedRequest() {
    Response response = resources.getJerseyTest().target(String.format("/v2/keys/%s/1", EXISTS_UUID)).request()
        .header(OptionalAccess.UNIDENTIFIED, AuthHelper.getUnidentifiedAccessHeader("9999".getBytes())).get();

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoMoreInteractions(keysScyllaDb);
  }

  @Test
  void testMalformedUnidentifiedRequest() {
    Response response = resources.getJerseyTest().target(String.format("/v2/keys/%s/1", EXISTS_UUID)).request()
        .header(OptionalAccess.UNIDENTIFIED, "$$$$$$$$$").get();

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoMoreInteractions(keysScyllaDb);
  }

  @Test
  void validMultiRequestTestV2() {
    PreKeyResponse results = resources.getJerseyTest()
        .target(String.format("/v2/keys/%s/*", EXISTS_UUID.toString())).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(PreKeyResponse.class);

    assertThat(results.getDevicesCount()).isEqualTo(3);
    assertThat(results.getIdentityKey()).isEqualTo(existsAccount.getIdentityKey());

    PreKey signedPreKey = results.getDevice(1).getSignedPreKey();
    PreKey preKey = results.getDevice(1).getPreKey();
    long registrationId = results.getDevice(1).getRegistrationId();
    long deviceId = results.getDevice(1).getDeviceId();

    assertThat(preKey.getKeyId()).isEqualTo(SAMPLE_KEY.getKeyId());
    assertThat(preKey.getPublicKey()).isEqualTo(SAMPLE_KEY.getPublicKey());
    assertThat(registrationId).isEqualTo(SAMPLE_REGISTRATION_ID);
    assertThat(signedPreKey.getKeyId()).isEqualTo(SAMPLE_SIGNED_KEY.getKeyId());
    assertThat(signedPreKey.getPublicKey()).isEqualTo(SAMPLE_SIGNED_KEY.getPublicKey());
    assertThat(deviceId).isEqualTo(1);

    signedPreKey = results.getDevice(2).getSignedPreKey();
    preKey = results.getDevice(2).getPreKey();
    registrationId = results.getDevice(2).getRegistrationId();
    deviceId = results.getDevice(2).getDeviceId();

    assertThat(preKey.getKeyId()).isEqualTo(SAMPLE_KEY2.getKeyId());
    assertThat(preKey.getPublicKey()).isEqualTo(SAMPLE_KEY2.getPublicKey());
    assertThat(registrationId).isEqualTo(SAMPLE_REGISTRATION_ID2);
    assertThat(signedPreKey.getKeyId()).isEqualTo(SAMPLE_SIGNED_KEY2.getKeyId());
    assertThat(signedPreKey.getPublicKey()).isEqualTo(SAMPLE_SIGNED_KEY2.getPublicKey());
    assertThat(deviceId).isEqualTo(2);

    signedPreKey = results.getDevice(4).getSignedPreKey();
    preKey = results.getDevice(4).getPreKey();
    registrationId = results.getDevice(4).getRegistrationId();
    deviceId = results.getDevice(4).getDeviceId();

    assertThat(preKey.getKeyId()).isEqualTo(SAMPLE_KEY4.getKeyId());
    assertThat(preKey.getPublicKey()).isEqualTo(SAMPLE_KEY4.getPublicKey());
    assertThat(registrationId).isEqualTo(SAMPLE_REGISTRATION_ID4);
    assertThat(signedPreKey).isNull();
    assertThat(deviceId).isEqualTo(4);

    verify(keysScyllaDb).take(eq(existsAccount));
    verifyNoMoreInteractions(keysScyllaDb);
  }

  @Test
  void invalidRequestTestV2() {
    Response response = resources.getJerseyTest().target(String.format("/v2/keys/%s", NOT_EXISTS_UUID)).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get();

    assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(404);
  }

  @Test
  void anotherInvalidRequestTestV2() {
    Response response = resources.getJerseyTest().target(String.format("/v2/keys/%s/22", EXISTS_UUID)).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get();

    assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(404);
  }

  @Test
  void unauthorizedRequestTestV2() {
    Response response = resources.getJerseyTest().target(String.format("/v2/keys/%s/1", EXISTS_UUID)).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.INVALID_PASSWORD))
        .get();

    assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(401);

    response = resources.getJerseyTest().target(String.format("/v2/keys/%s/1", EXISTS_UUID)).request().get();

    assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(401);
  }

  @Test
  void putKeysTestV2() {
    final PreKey preKey = new PreKey(31337, "foobar");
    final SignedPreKey signedPreKey = new SignedPreKey(31338, "foobaz", "myvalidsig");
    final String identityKey = "barbar";

    List<PreKey> preKeys = new LinkedList<PreKey>() {
      {
        add(preKey);
      }
    };

    PreKeyState preKeyState = new PreKeyState(identityKey, signedPreKey, preKeys);

    Response response = resources.getJerseyTest().target("/v2/keys").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.entity(preKeyState, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
    verify(keysScyllaDb).store(eqUuid(AuthHelper.VALID_ACCOUNT), eq(1L), listCaptor.capture());

    List<PreKey> capturedList = listCaptor.getValue();
    assertThat(capturedList.size()).isEqualTo(1);
    assertThat(capturedList.get(0).getKeyId()).isEqualTo(31337);
    assertThat(capturedList.get(0).getPublicKey()).isEqualTo("foobar");

    verify(AuthHelper.VALID_ACCOUNT).setIdentityKey(eq("barbar"));
    verify(AuthHelper.VALID_DEVICE).setSignedPreKey(eq(signedPreKey));
    verify(accounts).update(eq(AuthHelper.VALID_ACCOUNT), any());
  }

  @Test
  void disabledPutKeysTestV2() {
    final PreKey preKey = new PreKey(31337, "foobar");
    final SignedPreKey signedPreKey = new SignedPreKey(31338, "foobaz", "myvalidsig");
    final String identityKey = "barbar";

    List<PreKey> preKeys = new LinkedList<PreKey>() {
      {
        add(preKey);
      }
    };

    PreKeyState preKeyState = new PreKeyState(identityKey, signedPreKey, preKeys);

    Response response = resources.getJerseyTest().target("/v2/keys").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
        .put(Entity.entity(preKeyState, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
    verify(keysScyllaDb).store(eqUuid(AuthHelper.DISABLED_ACCOUNT), eq(1L), listCaptor.capture());

    List<PreKey> capturedList = listCaptor.getValue();
    assertThat(capturedList.size()).isEqualTo(1);
    assertThat(capturedList.get(0).getKeyId()).isEqualTo(31337);
    assertThat(capturedList.get(0).getPublicKey()).isEqualTo("foobar");

    verify(AuthHelper.DISABLED_ACCOUNT).setIdentityKey(eq("barbar"));
    verify(AuthHelper.DISABLED_DEVICE).setSignedPreKey(eq(signedPreKey));
    verify(accounts).update(eq(AuthHelper.DISABLED_ACCOUNT), any());
  }
  
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testRateLimitChallenge(boolean clientBelowMinimumVersion) throws RateLimitExceededException {

    Duration retryAfter = Duration.ofMinutes(1);
    doThrow(new RateLimitExceededException(retryAfter))
        .when(preKeyRateLimiter).validate(any());

    when(
        rateLimitChallengeManager.isClientBelowMinimumVersion("Shadow-Android/5.1.2 Android/30")).thenReturn(
        clientBelowMinimumVersion);
    when(rateLimitChallengeManager.getChallengeOptions(AuthHelper.VALID_ACCOUNT))
    .thenReturn(
        List.of(RateLimitChallengeManager.OPTION_PUSH_CHALLENGE, RateLimitChallengeManager.OPTION_RECAPTCHA));

    Response result = resources.getJerseyTest()
        .target(String.format("/v2/keys/%s/*", EXISTS_UUID.toString()))
        .request()
        .header(OptionalAccess.UNIDENTIFIED, AuthHelper.getUnidentifiedAccessHeader("1337".getBytes()))
        .header("User-Agent", "Shadow-Android/5.1.2 Android/30")
        .get();

    // unidentified access should not be rate limited
    assertThat(result.getStatus()).isEqualTo(200);

    result = resources.getJerseyTest()
        .target(String.format("/v2/keys/%s/*", EXISTS_UUID.toString()))
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .header("User-Agent", "Shadow-Android/5.1.2 Android/30")
        .get();

    if (clientBelowMinimumVersion) {
      assertThat(result.getStatus()).isEqualTo(508);
    } else {
      assertThat(result.getStatus()).isEqualTo(428);

    RateLimitChallenge rateLimitChallenge = result.readEntity(RateLimitChallenge.class);

    assertThat(rateLimitChallenge.getToken()).isNotBlank();
    assertThat(rateLimitChallenge.getOptions()).isNotEmpty();
    assertThat(rateLimitChallenge.getOptions()).contains("recaptcha");
    assertThat(rateLimitChallenge.getOptions()).contains("pushChallenge");
    assertThat(Long.parseLong(result.getHeaderString("Retry-After"))).isEqualTo(retryAfter.toSeconds());
    }
  }
}