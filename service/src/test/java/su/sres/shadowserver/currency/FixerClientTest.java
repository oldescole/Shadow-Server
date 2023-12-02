package su.sres.shadowserver.currency;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static su.sres.shadowserver.util.JsonHelpers.jsonFixture;

public class FixerClientTest {

  @Test
  public void testGetConversionsForBase() throws IOException, InterruptedException {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(jsonFixture("fixtures/fixer.res.json"));

    HttpClient httpClient = mock(HttpClient.class);
    when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenReturn(httpResponse);

    FixerClient fixerClient = new FixerClient(httpClient, "foo", false);
    Map<String, BigDecimal> conversions = fixerClient.getConversionsForBase("EUR");
    assertThat(conversions.get("CAD")).isEqualTo(new BigDecimal("1.560132"));
  }

}