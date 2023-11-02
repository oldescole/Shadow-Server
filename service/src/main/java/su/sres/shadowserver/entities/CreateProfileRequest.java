/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.apache.commons.lang3.StringUtils;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import su.sres.shadowserver.util.ExactlySize;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class CreateProfileRequest {

  @JsonProperty
  @NotEmpty
  private String version;

  @JsonProperty
  @ExactlySize({ 108, 380 })
  private String name;

  @JsonProperty
  private boolean avatar;

  @JsonProperty
  @ExactlySize({ 0, 80 })
  private String aboutEmoji;

  @JsonProperty
  @ExactlySize({ 0, 208, 376, 720 })
  private String about;

  @JsonProperty
  @ExactlySize({ 0, 776 })
  private String paymentAddress;
  
  @JsonProperty
  @NotNull
  private List<String> badgeIds = new ArrayList<>();

  @JsonProperty
  @NotNull
  @JsonDeserialize(using = ProfileKeyCommitmentAdapter.Deserializing.class)
  @JsonSerialize(using = ProfileKeyCommitmentAdapter.Serializing.class)
  private ProfileKeyCommitment commitment;

  public CreateProfileRequest() {
  }

  public CreateProfileRequest(
      ProfileKeyCommitment commitment, String version, String name, String aboutEmoji, String about,
      String paymentAddress, boolean wantsAvatar, List<String> badgeIds) {
    this.commitment = commitment;
    this.version = version;
    this.name = name;
    this.aboutEmoji = aboutEmoji;
    this.about = about;
    this.paymentAddress = paymentAddress;
    this.avatar = wantsAvatar;
    this.badgeIds = badgeIds;
  }

  public ProfileKeyCommitment getCommitment() {
    return commitment;
  }

  public String getVersion() {
    return version;
  }

  public String getName() {
    return name;
  }

  public boolean isAvatar() {
    return avatar;
  }

  public String getAboutEmoji() {
    return StringUtils.stripToNull(aboutEmoji);
  }

  public String getAbout() {
    return StringUtils.stripToNull(about);
  }

  public String getPaymentAddress() {
    return StringUtils.stripToNull(paymentAddress);
  }
  
  public List<String> getBadges() {
    return badgeIds;
  }
}
