package io.unitycatalog.server.service.iceberg;

/**
 * Resolves a custom S3 endpoint from the environment so Unity Catalog can run against a local
 * S3-compatible store (e.g. SeaweedFS or MinIO) for demos and Docker Compose setups.
 *
 * <p>This reads the standard AWS environment variables {@code AWS_ENDPOINT_URL_S3} (preferred) and
 * {@code AWS_ENDPOINT_URL} (fallback). The AWS SDK for Java v2 (>= 2.28.1) already auto-applies
 * {@code AWS_ENDPOINT_URL_S3} to clients it builds, but it does not infer path-style addressing,
 * which S3-compatible stores require, and it does not populate the Iceberg client config returned
 * to external engines. This helper lets both the server's own S3 client and the client-facing
 * config agree on when a custom endpoint is in effect.
 */
final class S3EndpointResolver {

  private S3EndpointResolver() {}

  /**
   * @return the configured custom S3 endpoint, or {@code null} if none is set (i.e. real AWS S3).
   */
  static String customEndpoint() {
    String endpoint = System.getenv("AWS_ENDPOINT_URL_S3");
    if (endpoint == null || endpoint.isBlank()) {
      endpoint = System.getenv("AWS_ENDPOINT_URL");
    }
    return (endpoint == null || endpoint.isBlank()) ? null : endpoint.trim();
  }

  /**
   * Path-style addressing is required by S3-compatible stores and is enabled whenever a custom
   * endpoint is configured. When talking to real AWS S3 (no custom endpoint), virtual-hosted style
   * is kept.
   */
  static boolean isPathStyleAccess() {
    return customEndpoint() != null;
  }
}
