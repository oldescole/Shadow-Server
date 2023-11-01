/*
 * Original software: Copyright 2013-2021 Signal Messenger, LLC
 * Modified software: Copyright 2019-2023 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Condition;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.MinioException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import su.sres.shadowserver.auth.AuthenticatedAccount;
import su.sres.shadowserver.auth.DisabledPermittedAuthenticatedAccount;
import su.sres.shadowserver.entities.Badge;
import su.sres.shadowserver.entities.CreateProfileRequest;
import su.sres.shadowserver.entities.Profile;
import su.sres.shadowserver.entities.ProfileAvatarUploadAttributes;
import su.sres.shadowserver.limits.RateLimiter;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.s3.PolicySigner;
import su.sres.shadowserver.s3.PostPolicyGenerator;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.AccountsManager;
import su.sres.shadowserver.storage.ProfilesManager;
import su.sres.shadowserver.storage.UsernamesManager;
import su.sres.shadowserver.storage.VersionedProfile;
import su.sres.shadowserver.util.AccountsHelper;
import su.sres.shadowserver.util.AuthHelper;
import su.sres.shadowserver.util.SystemMapper;

@ExtendWith(DropwizardExtensionsSupport.class)
class ProfileControllerTest {

  private static AccountsManager accountsManager = mock(AccountsManager.class);
  private static ProfilesManager profilesManager = mock(ProfilesManager.class);
  private static UsernamesManager usernamesManager = mock(UsernamesManager.class);
  private static RateLimiters rateLimiters = mock(RateLimiters.class);
  private static RateLimiter rateLimiter = mock(RateLimiter.class);
  private static RateLimiter usernameRateLimiter = mock(RateLimiter.class);
  private static MinioClient minioClient = mock(MinioClient.class);
  private static PostPolicyGenerator postPolicyGenerator = new PostPolicyGenerator("us-east-1", "profile-bucket",
      "accessKey");
  private static PolicySigner policySigner = new PolicySigner("accessSecret", "us-east-1");
  private static ServerZkProfileOperations zkProfileOperations = mock(ServerZkProfileOperations.class);

  private Account profileAccount;

  public static final ResourceExtension resources;
  
  static {
    try {
      resources = ResourceExtension.builder().addProvider(AuthHelper.getAuthFilter())
          .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(
              ImmutableSet.of(AuthenticatedAccount.class, DisabledPermittedAuthenticatedAccount.class)))
          .setMapper(SystemMapper.getMapper()).setTestContainerFactory(new GrizzlyWebTestContainerFactory())
          .addResource(new ProfileController(rateLimiters, accountsManager, profilesManager, usernamesManager,
              (acceptableLanguages, accountBadges) -> {
                try {
                  return List.of(
                      new Badge("TEST1", "other", new URL("https://example.com/badge/1"), "Test Badge", "This badge is in unit tests.")
                  );
                } catch (MalformedURLException e) {
                  throw new AssertionError(e);
                }
              },
              minioClient, postPolicyGenerator, policySigner, "profiles", zkProfileOperations,
              true))
          .build();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create ResourceExtension instance in static block.", e);
    }
  }

  @BeforeEach
  void setup() {

    reset(minioClient);
    
    AccountsHelper.setupMockUpdate(accountsManager);

    when(rateLimiters.getProfileLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getUsernameLookupLimiter()).thenReturn(usernameRateLimiter);

    profileAccount = mock(Account.class);

    when(profileAccount.getIdentityKey()).thenReturn("bar");
    when(profileAccount.getProfileName()).thenReturn("baz");
    when(profileAccount.getAvatar()).thenReturn("bang");
    when(profileAccount.getUuid()).thenReturn(AuthHelper.VALID_UUID_TWO);
    when(profileAccount.isEnabled()).thenReturn(true);
    when(profileAccount.isGroupsV2Supported()).thenReturn(false);
    when(profileAccount.isGv1MigrationSupported()).thenReturn(false);
    when(profileAccount.isSenderKeySupported()).thenReturn(false);
    when(profileAccount.isAnnouncementGroupSupported()).thenReturn(false);
    when(profileAccount.isChangeUserLoginSupported()).thenReturn(false);
    when(profileAccount.getCurrentProfileVersion()).thenReturn(Optional.empty());

    Account capabilitiesAccount = mock(Account.class);

    when(capabilitiesAccount.getIdentityKey()).thenReturn("barz");
    when(capabilitiesAccount.getProfileName()).thenReturn("bazz");
    when(capabilitiesAccount.getAvatar()).thenReturn("bangz");
    when(capabilitiesAccount.isEnabled()).thenReturn(true);
    when(capabilitiesAccount.isGroupsV2Supported()).thenReturn(true);
    when(capabilitiesAccount.isGv1MigrationSupported()).thenReturn(true);
    when(capabilitiesAccount.isSenderKeySupported()).thenReturn(true);
    when(capabilitiesAccount.isAnnouncementGroupSupported()).thenReturn(true);
    when(capabilitiesAccount.isChangeUserLoginSupported()).thenReturn(true);

    when(accountsManager.get(AuthHelper.VALID_NUMBER_TWO)).thenReturn(Optional.of(profileAccount));
    when(accountsManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of(profileAccount));
    when(usernamesManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of("n00bkiller"));
    when(usernamesManager.get("n00bkiller")).thenReturn(Optional.of(AuthHelper.VALID_UUID_TWO));
    
    when(accountsManager.get(AuthHelper.VALID_NUMBER)).thenReturn(Optional.of(capabilitiesAccount));
    when(accountsManager.get(AuthHelper.VALID_UUID)).thenReturn(Optional.of(capabilitiesAccount));

    when(profilesManager.get(eq(AuthHelper.VALID_UUID), eq("someversion"))).thenReturn(Optional.empty());
    when(profilesManager.get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"))).thenReturn(Optional.of(new VersionedProfile(
        "validversion", "validname", "validavatar", "emoji", "about", null, "validcommitmnet".getBytes())));

    clearInvocations(rateLimiter);
    clearInvocations(accountsManager);
    clearInvocations(usernamesManager);
    clearInvocations(usernameRateLimiter);
    clearInvocations(profilesManager);
  }
  
  @AfterEach
  void teardown() {
    reset(accountsManager);
  }

  @Test
  void testProfileGetByUuid() throws RateLimitExceededException {
    Profile profile = resources.getJerseyTest().target("/v1/profile/" + AuthHelper.VALID_UUID_TWO).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("bang");
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");
    assertThat(profile.getBadges()).hasSize(1).element(0).has(new Condition<>(
        badge -> "Test Badge".equals(badge.getName()), "has badge with expected name"));

    verify(accountsManager).get(AuthHelper.VALID_UUID_TWO);
    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(rateLimiter, times(1)).validate(AuthHelper.VALID_UUID);
  }  

  @Test
  void testProfileGetByUsername() throws RateLimitExceededException {
    Profile profile = resources.getJerseyTest().target("/v1/profile/username/n00bkiller").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("bang");
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");    
    assertThat(profile.getUuid()).isEqualTo(AuthHelper.VALID_UUID_TWO);
    assertThat(profile.getBadges()).hasSize(1).element(0).has(new Condition<>(
        badge -> "Test Badge".equals(badge.getName()), "has badge with expected name"));

    verify(accountsManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(usernamesManager, times(1)).get(eq("n00bkiller"));
    verify(usernameRateLimiter, times(1)).validate(eq(AuthHelper.VALID_UUID));
  }

  @Test
  void testProfileGetUnauthorized() {
    Response response = resources.getJerseyTest().target("/v1/profile/" + AuthHelper.VALID_UUID_TWO).request()
        .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testProfileGetByUsernameUnauthorized() {
    Response response = resources.getJerseyTest().target("/v1/profile/username/n00bkiller").request().get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testProfileGetByUsernameNotFound() throws RateLimitExceededException {
    Response response = resources.getJerseyTest().target("/v1/profile/username/n00bkillerzzzzz").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get();

    assertThat(response.getStatus()).isEqualTo(404);

    verify(usernamesManager, times(1)).get(eq("n00bkillerzzzzz"));
    verify(usernameRateLimiter, times(1)).validate(eq(AuthHelper.VALID_UUID));
  }

  @Test
  void testProfileGetDisabled() {
    Response response = resources.getJerseyTest().target("/v1/profile/" + AuthHelper.VALID_UUID_TWO).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
        .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testProfileCapabilities() {
    Profile profile = resources.getJerseyTest().target("/v1/profile/" + AuthHelper.VALID_UUID).request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);

    assertThat(profile.getCapabilities().isGv2()).isEqualTo(true);
    assertThat(profile.getCapabilities().isGv1Migration()).isEqualTo(true);
    assertThat(profile.getCapabilities().isSenderKey()).isTrue();
    assertThat(profile.getCapabilities().isAnnouncementGroup()).isTrue();

    profile = resources
        .getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .get(Profile.class);

    assertThat(profile.getCapabilities().isGv2()).isFalse();
    assertThat(profile.getCapabilities().isGv1Migration()).isFalse();
    assertThat(profile.getCapabilities().isSenderKey()).isFalse();
    assertThat(profile.getCapabilities().isAnnouncementGroup()).isFalse();
  }

  @Test
  void testSetProfileNameDeprecated() {
    Response response = resources.getJerseyTest()
        .target("/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(accountsManager, times(1)).update(any(Account.class), any());
  }

  @Test
  void testSetProfileNameExtendedDeprecated() {
    Response response = resources.getJerseyTest().target(
        "/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(accountsManager, times(1)).update(any(Account.class), any());
  }

  @Test
  void testSetProfileNameWrongSizeDeprecated() {
    Response response = resources.getJerseyTest().target(
        "/v1/profile/name/1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
    verifyNoMoreInteractions(accountsManager);
  }

/////

  @Test
  void testSetProfileWantAvatarUpload() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    ProfileAvatarUploadAttributes uploadAttributes = resources.getJerseyTest().target("/v1/profile/").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", null, null,
            null, true), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID), eq("someversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID), profileArgumentCaptor.capture());

    verifyNoMoreInteractions(minioClient);

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).isEqualTo(uploadAttributes.getKey());
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("someversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo(
        "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();
  }

  @Test
  void testSetProfileWantAvatarUploadWithBadProfileSize() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    Response response = resources.getJerseyTest().target("/v1/profile/").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890", null, null,
            null, true), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  void testSetProfileWithoutAvatarUpload() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    Response response = resources.getJerseyTest().target("/v1/profile/").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "anotherversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", null, null,
            null, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("anotherversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verify(AuthHelper.VALID_ACCOUNT_TWO).setProfileName("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    verify(AuthHelper.VALID_ACCOUNT_TWO).setAvatar(null);

    verifyNoMoreInteractions(minioClient);

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).isNull();
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("anotherversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo(
        "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();
  }

  @Test
  void testSetProfileWithAvatarUploadAndPreviousAvatar()
      throws InvalidInputException, MinioException, InvalidKeyException, IOException, NoSuchAlgorithmException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    ProfileAvatarUploadAttributes uploadAttributes = resources.getJerseyTest().target("/v1/profile/").request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "validversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", null, null,
            null, true), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);
    ArgumentCaptor<RemoveObjectArgs> argsCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
    
    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());
    verify(minioClient, times(1)).removeObject(argsCaptor.capture());

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("validversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo(
        "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();
    
    assertThat(argsCaptor.getValue().bucket()).isEqualTo("profiles");
    assertThat(argsCaptor.getValue().object()).isEqualTo("validavatar");
  }

  @Test
  void testSetProfileExtendedName() throws InvalidInputException, InvalidKeyException, ErrorResponseException, IllegalArgumentException, InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException, ServerException, XmlParserException, IOException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);

    resources.getJerseyTest()
        .target("/v1/profile/")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "validversion", name, null, null, null, true), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);
    ArgumentCaptor<RemoveObjectArgs> argsCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());
    verify(minioClient, times(1)).removeObject(argsCaptor.capture());

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("validversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo(name);
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();
    assertThat(argsCaptor.getValue().bucket()).isEqualTo("profiles");
    assertThat(argsCaptor.getValue().object()).isEqualTo("validavatar");
  }

  @Test
  void testSetProfileEmojiAndBioText() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String emoji = RandomStringUtils.randomAlphanumeric(80);
    final String text = RandomStringUtils.randomAlphanumeric(720);

    Response response = resources.getJerseyTest()
        .target("/v1/profile/")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "anotherversion", name, emoji, text, null, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("anotherversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verify(AuthHelper.VALID_ACCOUNT_TWO).setProfileName(name);
    verify(AuthHelper.VALID_ACCOUNT_TWO).setAvatar(null);

    verifyNoMoreInteractions(minioClient);

    final VersionedProfile profile = profileArgumentCaptor.getValue();
    assertThat(profile.getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profile.getAvatar()).isNull();
    assertThat(profile.getVersion()).isEqualTo("anotherversion");
    assertThat(profile.getName()).isEqualTo(name);
    assertThat(profile.getAboutEmoji()).isEqualTo(emoji);
    assertThat(profile.getAbout()).isEqualTo(text);
    assertThat(profile.getPaymentAddress()).isNull();
  }

  @Test
  void testSetProfilePaymentAddress() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String paymentAddress = RandomStringUtils.randomAlphanumeric(776);

    Response response = resources.getJerseyTest()
        .target("/v1/profile")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "yetanotherversion", name, null, null, paymentAddress, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager).get(eq(AuthHelper.VALID_UUID_TWO), eq("yetanotherversion"));
    verify(profilesManager).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verify(AuthHelper.VALID_ACCOUNT_TWO).setProfileName(eq(name));
    verify(AuthHelper.VALID_ACCOUNT_TWO).setAvatar(null);

    verifyNoMoreInteractions(minioClient);

    final VersionedProfile profile = profileArgumentCaptor.getValue();
    assertThat(profile.getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profile.getAvatar()).isNull();
    assertThat(profile.getVersion()).isEqualTo("yetanotherversion");
    assertThat(profile.getName()).isEqualTo(name);
    assertThat(profile.getAboutEmoji()).isNull();
    assertThat(profile.getAbout()).isNull();
    assertThat(profile.getPaymentAddress()).isEqualTo(paymentAddress);
  }

  @Test
  void testGetProfileByVersion() throws RateLimitExceededException {
    Profile profile = resources.getJerseyTest().target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("validname");
    assertThat(profile.getAbout()).isEqualTo("about");
    assertThat(profile.getAboutEmoji()).isEqualTo("emoji");
    assertThat(profile.getAvatar()).isEqualTo("validavatar");
    assertThat(profile.getCapabilities().isGv2()).isFalse();
    assertThat(profile.getCapabilities().isGv1Migration()).isFalse();
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");
    assertThat(profile.getUuid()).isNull();

    verify(accountsManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));

    verify(rateLimiter, times(1)).validate(AuthHelper.VALID_UUID);
  }

  @Test
  void testSetProfileUpdatesAccountCurrentVersion() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String paymentAddress = RandomStringUtils.randomAlphanumeric(776);

    Response response = resources.getJerseyTest()
        .target("/v1/profile")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", name, null, null, paymentAddress, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    verify(AuthHelper.VALID_ACCOUNT_TWO).setCurrentProfileVersion("someversion");
  }

  @Test
  void testGetProfileReturnsNoPaymentAddressIfCurrentVersionMismatch() {
    when(profilesManager.get(AuthHelper.VALID_UUID_TWO, "validversion")).thenReturn(
        Optional.of(new VersionedProfile(null, null, null, null, null, "paymentaddress", null)));
    Profile profile = resources.getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);
    assertThat(profile.getPaymentAddress()).isEqualTo("paymentaddress");

    when(profileAccount.getCurrentProfileVersion()).thenReturn(Optional.of("validversion"));
    profile = resources.getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);
    assertThat(profile.getPaymentAddress()).isEqualTo("paymentaddress");

    when(profileAccount.getCurrentProfileVersion()).thenReturn(Optional.of("someotherversion"));
    profile = resources.getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
        .get(Profile.class);
    assertThat(profile.getPaymentAddress()).isNull();
    assertThat(profile.getBadges()).hasSize(1).element(0).has(new Condition<>(
        badge -> "Test Badge".equals(badge.getName()), "has badge with expected name"));

  }
}