package io.unitycatalog.server.service.iceberg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class S3EndpointResolverTest {

  private static Function<String, String> env(Map<String, String> values) {
    return values::get;
  }

  @Test
  public void returnsNullWhenNoEndpointConfigured() {
    assertThat(S3EndpointResolver.customEndpoint(env(Map.of()))).isNull();
  }

  @Test
  public void prefersS3SpecificVariable() {
    Map<String, String> values = new HashMap<>();
    values.put("AWS_ENDPOINT_URL_S3", "http://seaweedfs:8333");
    values.put("AWS_ENDPOINT_URL", "http://other:9000");
    assertThat(S3EndpointResolver.customEndpoint(env(values))).isEqualTo("http://seaweedfs:8333");
  }

  @Test
  public void fallsBackToGenericVariable() {
    Map<String, String> values = new HashMap<>();
    values.put("AWS_ENDPOINT_URL", "http://minio:9000");
    assertThat(S3EndpointResolver.customEndpoint(env(values))).isEqualTo("http://minio:9000");
  }

  @Test
  public void blankS3VariableFallsBackToGeneric() {
    Map<String, String> values = new HashMap<>();
    values.put("AWS_ENDPOINT_URL_S3", "   ");
    values.put("AWS_ENDPOINT_URL", "http://minio:9000");
    assertThat(S3EndpointResolver.customEndpoint(env(values))).isEqualTo("http://minio:9000");
  }

  @Test
  public void trimsWhitespace() {
    Map<String, String> values = new HashMap<>();
    values.put("AWS_ENDPOINT_URL_S3", "  http://seaweedfs:8333  ");
    assertThat(S3EndpointResolver.customEndpoint(env(values))).isEqualTo("http://seaweedfs:8333");
  }

  @Test
  public void blankEndpointsResolveToNull() {
    Map<String, String> values = new HashMap<>();
    values.put("AWS_ENDPOINT_URL_S3", "");
    values.put("AWS_ENDPOINT_URL", "  ");
    assertThat(S3EndpointResolver.customEndpoint(env(values))).isNull();
  }
}
