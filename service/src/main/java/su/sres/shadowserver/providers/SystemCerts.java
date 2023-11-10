/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.providers;

import java.io.IOException;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class SystemCerts {

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] shadowCertA;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] shadowCertB;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] cloudCertA;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] cloudCertB;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] storageCertA;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] storageCertB;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] sfuCertA;

  @JsonProperty
  @JsonSerialize(using = Serializing.class)
  private byte[] sfuCertB;

  public SystemCerts(byte[] cloudCertA, byte[] cloudCertB, byte[] shadowCertA, byte[] shadowCertB, byte[] storageCertA, byte[] storageCertB, byte[] sfuCertA, byte[] sfuCertB) {
    this.cloudCertA = cloudCertA;
    this.cloudCertB = cloudCertB;
    this.shadowCertA = shadowCertA;
    this.shadowCertB = shadowCertB;
    this.storageCertA = storageCertA;
    this.storageCertB = storageCertB;
    this.sfuCertA = sfuCertA;
    this.sfuCertB = sfuCertB;
  }

  public byte[] getCloudCertA() {
    return cloudCertA;
  }

  public byte[] getCloudCertB() {
    return cloudCertB;
  }

  public byte[] getShadowCertA() {
    return shadowCertA;
  }

  public byte[] getShadowCertB() {
    return shadowCertB;
  }

  public byte[] getStorageCertA() {
    return storageCertA;
  }

  public byte[] getStorageCertB() {
    return storageCertB;
  }

  public byte[] getSfuCertA() {
    return sfuCertA;
  }

  public byte[] getSfuCertB() {
    return sfuCertB;
  }

  public void setCloudCertA(byte[] cert) {
    cloudCertA = cert;
  }

  public void setCloudCertB(byte[] cert) {
    cloudCertB = cert;
  }

  public void setShadowCertA(byte[] cert) {
    shadowCertA = cert;
  }

  public void setShadowCertB(byte[] cert) {
    shadowCertB = cert;
  }

  public void setStorageCertA(byte[] cert) {
    storageCertA = cert;
  }

  public void setStorageCertB(byte[] cert) {
    storageCertB = cert;
  }

  public void setSfuCertA(byte[] cert) {
    sfuCertA = cert;
  }

  public void setSfuCertB(byte[] cert) {
    sfuCertB = cert;
  }

  public static class Serializing extends JsonSerializer<byte[]> {
    @Override
    public void serialize(byte[] bytes, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws IOException, JsonProcessingException {
      jsonGenerator.writeString(Base64.getEncoder().encodeToString(bytes));
    }
  }
}