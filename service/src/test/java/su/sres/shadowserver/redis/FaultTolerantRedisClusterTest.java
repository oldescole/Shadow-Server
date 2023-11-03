/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.redis;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.event.EventBus;
import io.lettuce.core.resource.ClientResources;

import org.junit.Before;
import org.junit.Test;
import su.sres.shadowserver.configuration.CircuitBreakerConfiguration;
import su.sres.shadowserver.configuration.RetryConfiguration;
import reactor.core.publisher.Flux;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

public class FaultTolerantRedisClusterTest {

  private RedisAdvancedClusterCommands<String, String> clusterCommands;
  private FaultTolerantRedisCluster faultTolerantCluster;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    final RedisClusterClient clusterClient = mock(RedisClusterClient.class);
    final StatefulRedisClusterConnection<String, String> clusterConnection = mock(StatefulRedisClusterConnection.class);
    final StatefulRedisClusterPubSubConnection<String, String> pubSubConnection = mock(StatefulRedisClusterPubSubConnection.class);
    final ClientResources clientResources = mock(ClientResources.class);
    final EventBus eventBus = mock(EventBus.class);

    clusterCommands = mock(RedisAdvancedClusterCommands.class);

    when(clusterClient.connect()).thenReturn(clusterConnection);
    when(clusterClient.connectPubSub()).thenReturn(pubSubConnection);
    when(clusterClient.getResources()).thenReturn(clientResources);
    when(clusterConnection.sync()).thenReturn(clusterCommands);
    when(clientResources.eventBus()).thenReturn(eventBus);
    when(eventBus.get()).thenReturn(mock(Flux.class));

    final CircuitBreakerConfiguration breakerConfiguration = new CircuitBreakerConfiguration();
    breakerConfiguration.setFailureRateThreshold(100);
    breakerConfiguration.setRingBufferSizeInClosedState(1);
    breakerConfiguration.setWaitDurationInOpenStateInSeconds(Integer.MAX_VALUE);

    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMaxAttempts(3);
    retryConfiguration.setWaitDuration(0);

    faultTolerantCluster = new FaultTolerantRedisCluster("test", clusterClient, Duration.ofSeconds(2), breakerConfiguration, retryConfiguration);
  }

  @Test
  public void testBreaker() {
    when(clusterCommands.get(anyString()))
        .thenReturn("value")
        .thenThrow(new RuntimeException("Badness has ensued."));

    assertEquals("value", faultTolerantCluster.withCluster(connection -> connection.sync().get("key")));

    assertThrows(RedisException.class,
        () -> faultTolerantCluster.withCluster(connection -> connection.sync().get("OH NO")));

    final RedisException redisException = assertThrows(RedisException.class,
        () -> faultTolerantCluster.withCluster(connection -> connection.sync().get("OH NO")));

    assertTrue(redisException.getCause() instanceof CallNotPermittedException);
  }

  @Test
  public void testRetry() {
    when(clusterCommands.get(anyString()))
        .thenThrow(new RedisCommandTimeoutException())
        .thenThrow(new RedisCommandTimeoutException())
        .thenReturn("value");

    assertEquals("value", faultTolerantCluster.withCluster(connection -> connection.sync().get("key")));

    when(clusterCommands.get(anyString()))
        .thenThrow(new RedisCommandTimeoutException())
        .thenThrow(new RedisCommandTimeoutException())
        .thenThrow(new RedisCommandTimeoutException())
        .thenReturn("value");

    assertThrows(RedisCommandTimeoutException.class, () -> faultTolerantCluster.withCluster(connection -> connection.sync().get("key")));
  }
}
