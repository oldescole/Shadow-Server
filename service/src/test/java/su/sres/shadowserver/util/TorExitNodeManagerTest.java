/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.util;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import su.sres.shadowserver.configuration.MinioConfiguration;
import su.sres.shadowserver.redis.RedisClusterExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest
class TorExitNodeManagerTest {

  @RegisterExtension
  static final RedisClusterExtension REDIS_CLUSTER_EXTENSION = RedisClusterExtension.builder().build();
   
  // @Rule
  // public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort().dynamicHttpsPort());
  
  @Test
  void testIsTorExitNode() {
    final MinioConfiguration configuration = mock(MinioConfiguration.class);
    
    when(configuration.getRegion()).thenReturn("ap-northeast-3");
    when(configuration.getUri()).thenReturn("http://localhost:9000");
    when(configuration.getAccessKey()).thenReturn("12345");
    when(configuration.getAccessSecret()).thenReturn("67890");
    
    final TorExitNodeManager torExitNodeManager =
        new TorExitNodeManager(mock(ScheduledExecutorService.class), configuration);   
    
    
    assertFalse(torExitNodeManager.isTorExitNode("10.0.0.1"));
    assertFalse(torExitNodeManager.isTorExitNode("10.0.0.2"));
    
    torExitNodeManager.handleExitListChangedStream(
        new ByteArrayInputStream("10.0.0.1\n10.0.0.2".getBytes(StandardCharsets.UTF_8)));

    assertTrue(torExitNodeManager.isTorExitNode("10.0.0.1"));
    assertTrue(torExitNodeManager.isTorExitNode("10.0.0.2"));
    assertFalse(torExitNodeManager.isTorExitNode("10.0.0.3"));
  }
}
