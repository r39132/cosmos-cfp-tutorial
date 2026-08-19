package com.sanand.cfp.admin;

import com.azure.core.management.Region;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.resourcemanager.cosmos.CosmosManager;
import com.azure.resourcemanager.cosmos.models.CosmosDBAccount;
import com.azure.resourcemanager.cosmos.models.DatabaseAccountListKeysResult;
import com.azure.resourcemanager.resources.ResourceManager;
import com.sanand.cfp.common.CfpConfig;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Creates everything the tutorial needs, idempotently.
 *
 * <p>Resource group → multi-region multi-write account → database → monitored container → lease
 * container. Re-running is safe: each step is skipped when the resource already exists.
 */
@Command(
    name = "provision",
    mixinStandardHelpOptions = true,
    description = "Create the resource group, Cosmos account, database and containers.")
final class ProvisionCommand implements Callable<Integer> {

  /**
   * The two write regions. Region 0 is the account's primary; with multi-write enabled both are
   * writable, which is the point of the exercise.
   */
  private static final Region PRIMARY_REGION = Region.US_SOUTH_CENTRAL;

  private static final Region SECONDARY_REGION = Region.US_WEST2;

  @Option(
      names = "--container-throughput",
      description = "Manual RU/s for the monitored container (default: ${DEFAULT-VALUE}).")
  private int containerThroughput = 400;

  @Option(
      names = "--lease-throughput",
      description = "Manual RU/s for the lease container (default: ${DEFAULT-VALUE}).")
  private int leaseThroughput = 400;

  @Option(
      names = "--single-write-region",
      description = "Provision with a single write region instead of the default two.")
  private boolean singleWriteRegion;

  @Override
  public Integer call() {
    String subscriptionId = CfpConfig.subscriptionId();
    String resourceGroup = CfpConfig.resourceGroup();
    String accountName = CfpConfig.accountName();

    System.out.printf("Subscription : %s%n", subscriptionId);
    System.out.printf("ResourceGroup: %s%n", resourceGroup);
    System.out.printf("Account      : %s%n", accountName);
    System.out.println();

    ensureResourceGroup(resourceGroup);
    CosmosDBAccount account = ensureAccount(resourceGroup, accountName);

    DatabaseAccountListKeysResult keys = account.listKeys();
    String endpoint = account.documentEndpoint();
    String primaryKey = keys.primaryMasterKey();

    ensureDataPlaneResources(endpoint, primaryKey);
    printSummary(subscriptionId, resourceGroup, accountName, account, endpoint, primaryKey);
    return 0;
  }

  private void ensureResourceGroup(String resourceGroup) {
    ResourceManager rm = AzureClients.resourceManager();
    if (rm.resourceGroups().contain(resourceGroup)) {
      System.out.printf("[=] Resource group '%s' already exists.%n", resourceGroup);
      return;
    }
    System.out.printf(
        "[+] Creating resource group '%s' in %s ...%n", resourceGroup, PRIMARY_REGION);
    rm.resourceGroups().define(resourceGroup).withRegion(PRIMARY_REGION).create();
  }

  private CosmosDBAccount ensureAccount(String resourceGroup, String accountName) {
    CosmosManager manager = AzureClients.cosmosManager();

    CosmosDBAccount existing = tryGetAccount(manager, resourceGroup, accountName);
    if (existing != null) {
      System.out.printf(
          "[=] Cosmos account '%s' already exists (multi-write=%s, consistency=%s).%n",
          accountName,
          existing.multipleWriteLocationsEnabled(),
          existing.consistencyPolicy().defaultConsistencyLevel());
      return existing;
    }

    System.out.printf(
        "[+] Creating Cosmos account '%s' (SQL API, Session consistency, regions %s + %s%s) ...%n",
        accountName,
        PRIMARY_REGION.label(),
        SECONDARY_REGION.label(),
        singleWriteRegion ? ", single write region" : ", both writable");
    System.out.println("    This typically takes 5-10 minutes. Grab a coffee.");

    return manager
        .databaseAccounts()
        .define(accountName)
        .withRegion(PRIMARY_REGION)
        .withExistingResourceGroup(resourceGroup)
        .withDataModelSql()
        .withSessionConsistency()
        .withWriteReplication(PRIMARY_REGION)
        .withReadReplication(SECONDARY_REGION)
        .withMultipleWriteLocationsEnabled(!singleWriteRegion)
        .create();
  }

  private CosmosDBAccount tryGetAccount(
      CosmosManager manager, String resourceGroup, String accountName) {
    try {
      return manager.databaseAccounts().getByResourceGroup(resourceGroup, accountName);
    } catch (RuntimeException e) {
      // The fluent SDK throws (rather than returning null) when the account is absent.
      return null;
    }
  }

  /**
   * Creates the database and both containers over the data plane.
   *
   * <p>The lease container is partitioned on {@code /id} — that is what the Change Feed Processor
   * requires, and getting it wrong is the single most common CFP setup mistake.
   */
  private void ensureDataPlaneResources(String endpoint, String key) {
    String databaseName = CfpConfig.databaseName();
    String containerName = CfpConfig.containerName();
    String leaseContainerName = CfpConfig.leaseContainerName();

    try (CosmosClient client =
        new CosmosClientBuilder()
            .endpoint(endpoint)
            .key(key)
            .userAgentSuffix("cosmos-cfp-tutorial-admin")
            .buildClient()) {

      System.out.printf("[+] Ensuring database '%s' ...%n", databaseName);
      client.createDatabaseIfNotExists(databaseName);
      var database = client.getDatabase(databaseName);

      System.out.printf(
          "[+] Ensuring container '%s' (pk=%s, %d RU/s) ...%n",
          containerName, CfpConfig.PARTITION_KEY_PATH, containerThroughput);
      database.createContainerIfNotExists(
          new CosmosContainerProperties(containerName, CfpConfig.PARTITION_KEY_PATH),
          ThroughputProperties.createManualThroughput(containerThroughput));

      System.out.printf(
          "[+] Ensuring lease container '%s' (pk=/id, %d RU/s) ...%n",
          leaseContainerName, leaseThroughput);
      database.createContainerIfNotExists(
          new CosmosContainerProperties(leaseContainerName, "/id"),
          ThroughputProperties.createManualThroughput(leaseThroughput));
    }
  }

  private void printSummary(
      String subscriptionId,
      String resourceGroup,
      String accountName,
      CosmosDBAccount account,
      String endpoint,
      String primaryKey) {

    System.out.println();
    System.out.println("=== Provisioned ===========================================");
    System.out.printf("Consistency  : %s%n", account.consistencyPolicy().defaultConsistencyLevel());
    System.out.printf("Multi-write  : %s%n", account.multipleWriteLocationsEnabled());
    System.out.println("Regions      :");
    account
        .writableReplications()
        .forEach(
            loc ->
                System.out.printf(
                    "  - %s (write, priority %d)%n", loc.locationName(), loc.failoverPriority()));

    System.out.println();
    System.out.println("Export these for cfp-writer and cfp-reader:");
    System.out.println();
    System.out.printf("  export ACCOUNT_HOST='%s'%n", endpoint);
    System.out.printf("  export ACCOUNT_KEY='%s'%n", primaryKey);
    System.out.printf("  export COSMOS_DATABASE='%s'%n", CfpConfig.databaseName());
    System.out.printf("  export COSMOS_CONTAINER='%s'%n", CfpConfig.containerName());
    System.out.printf("  export COSMOS_LEASE_CONTAINER='%s'%n", CfpConfig.leaseContainerName());
    System.out.println();
    LinksCommand.printLinks(subscriptionId, resourceGroup, accountName);
    System.out.println("===========================================================");
  }
}
