package com.sanand.cfp.common;

import java.util.Optional;

/**
 * Environment-driven configuration shared by all three apps.
 *
 * <p>Everything the tutorial needs is read from environment variables so that no key ever lands in
 * source control. {@code ACCOUNT_HOST} / {@code ACCOUNT_KEY} follow the naming used by the official
 * azure-cosmos-java-sql-api-samples repo, so an existing shell profile keeps working.
 */
public final class CfpConfig {

  /** Data-plane account endpoint, e.g. {@code https://my-acct.documents.azure.com:443/}. */
  public static final String ENV_ACCOUNT_HOST = "ACCOUNT_HOST";

  /** Data-plane primary (or secondary) master key. */
  public static final String ENV_ACCOUNT_KEY = "ACCOUNT_KEY";

  public static final String ENV_DATABASE = "COSMOS_DATABASE";
  public static final String ENV_CONTAINER = "COSMOS_CONTAINER";
  public static final String ENV_LEASE_CONTAINER = "COSMOS_LEASE_CONTAINER";
  public static final String ENV_PREFERRED_REGION = "COSMOS_PREFERRED_REGION";

  public static final String ENV_SUBSCRIPTION_ID = "AZURE_SUBSCRIPTION_ID";
  public static final String ENV_RESOURCE_GROUP = "AZURE_RESOURCE_GROUP";
  public static final String ENV_ACCOUNT_NAME = "COSMOS_ACCOUNT_NAME";

  public static final String DEFAULT_DATABASE = "cfpdb";
  public static final String DEFAULT_CONTAINER = "sensor-readings";
  public static final String DEFAULT_LEASE_CONTAINER = "leases";
  public static final String DEFAULT_PREFERRED_REGION = "South Central US";

  /** Partition key path of the monitored container. */
  public static final String PARTITION_KEY_PATH = "/deviceId";

  private CfpConfig() {}

  public static String accountHost() {
    return required(ENV_ACCOUNT_HOST);
  }

  public static String accountKey() {
    return required(ENV_ACCOUNT_KEY);
  }

  public static String databaseName() {
    return optional(ENV_DATABASE).orElse(DEFAULT_DATABASE);
  }

  public static String containerName() {
    return optional(ENV_CONTAINER).orElse(DEFAULT_CONTAINER);
  }

  public static String leaseContainerName() {
    return optional(ENV_LEASE_CONTAINER).orElse(DEFAULT_LEASE_CONTAINER);
  }

  public static String preferredRegion() {
    return optional(ENV_PREFERRED_REGION).orElse(DEFAULT_PREFERRED_REGION);
  }

  public static String subscriptionId() {
    return required(ENV_SUBSCRIPTION_ID);
  }

  public static String resourceGroup() {
    return required(ENV_RESOURCE_GROUP);
  }

  public static String accountName() {
    return required(ENV_ACCOUNT_NAME);
  }

  /** Reads {@code name}, treating blank as absent. */
  public static Optional<String> optional(String name) {
    String value = System.getenv(name);
    return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
  }

  /** Reads {@code name} or fails with a message that says exactly what to export. */
  public static String required(String name) {
    return optional(name)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing required environment variable "
                        + name
                        + ". See .env.example, then: set -a && source .env && set +a"));
  }
}
