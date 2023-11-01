/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.VisibleForTesting;

import org.signal.zkgroup.profiles.ProfileKeyCredentialResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Profile {

  @JsonProperty
  private String identityKey;

  @JsonProperty
  private String name;

  @JsonProperty
  private String about;

  @JsonProperty
  private String aboutEmoji;

  @JsonProperty
  private String avatar;

  @JsonProperty
  private String paymentAddress;

  @JsonProperty
  private String unidentifiedAccess;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private UserCapabilities capabilities;

  @JsonProperty
  private String username;

  @JsonProperty
  private UUID uuid;

  @JsonProperty
  private List<Badge> badges;

  @JsonProperty
  @JsonSerialize(using = ProfileKeyCredentialResponseAdapter.Serializing.class)
  @JsonDeserialize(using = ProfileKeyCredentialResponseAdapter.Deserializing.class)
  private ProfileKeyCredentialResponse credential;

  public Profile() {
  }

  public Profile(
        String name, String about, String aboutEmoji, String avatar, String paymentAddress, String identityKey,
        String unidentifiedAccess, boolean unrestrictedUnidentifiedAccess, UserCapabilities capabilities, String username,
        UUID uuid, List<Badge> badges, ProfileKeyCredentialResponse credential) {
    this.name = name;
    this.about = about;
    this.aboutEmoji = aboutEmoji;
    this.avatar = avatar;
    this.paymentAddress = paymentAddress;
    this.identityKey = identityKey;
    this.unidentifiedAccess = unidentifiedAccess;
	this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
	this.capabilities = capabilities;
	this.username = username;
	this.uuid = uuid;	
	this.badges = badges;
	this.credential = credential;
    }

  @VisibleForTesting
  public String getIdentityKey() {
    return identityKey;
  }

  @VisibleForTesting
  public String getName() {
    return name;
  }

  public String getAbout() {
    return about;
  }

  public String getAboutEmoji() {
    return aboutEmoji;
  }

  @VisibleForTesting
  public String getAvatar() {
    return avatar;
  }

  public String getPaymentAddress() {
    return paymentAddress;
  }

  @VisibleForTesting
  public String getUnidentifiedAccess() {
    return unidentifiedAccess;
  }

  @VisibleForTesting
  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  @VisibleForTesting
  public UserCapabilities getCapabilities() {
    return capabilities;
  }

  @VisibleForTesting
  public String getUsername() {
    return username;
  }

  @VisibleForTesting
  public UUID getUuid() {
    return uuid;
  }

  public List<Badge> getBadges() {
    return badges;
  }
}