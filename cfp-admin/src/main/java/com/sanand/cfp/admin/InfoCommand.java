package com.sanand.cfp.admin;

import com.azure.cosmos.CosmosClient;
import com.azure.resourcemanager.cosmos.models.CosmosDBAccount;
import com.sanand.cfp.common.CfpConfig;
import com.sanand.cfp.common.CosmosClients;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Reports the current state of the account, database and containers. */
@Command(
    name = "info",
    mixinStandardHelpOptions = true,
    description = "Show the account's regions, consistency and container document counts.")
final class InfoCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    String resourceGroup = CfpConfig.resourceGroup();
    String accountName = CfpConfig.accountName();

    CosmosDBAccount account =
        AzureClients.cosmosManager()
            .databaseAccounts()
            .getByResourceGroup(resourceGroup, accountName);

    System.out.printf("Account      : %s%n", account.name());
    System.out.printf("Endpoint     : %s%n", account.documentEndpoint());
    System.out.printf("Consistency  : %s%n", account.consistencyPolicy().defaultConsistencyLevel());
    System.out.printf("Multi-write  : %s%n", account.multipleWriteLocationsEnabled());
    System.out.println("Write regions:");
    account
        .writableReplications()
        .forEach(
            loc ->
                System.out.printf(
                    "  - %s (priority %d)%n", loc.locationName(), loc.failoverPriority()));
    System.out.println("Read regions :");
    account
        .readableReplications()
        .forEach(loc -> System.out.printf("  - %s%n", loc.locationName()));

    System.out.println();
    countDocuments();
    return 0;
  }

  /** Uses ACCOUNT_HOST / ACCOUNT_KEY, so this half is skipped when they are not exported. */
  private void countDocuments() {
    if (CfpConfig.optional(CfpConfig.ENV_ACCOUNT_HOST).isEmpty()
        || CfpConfig.optional(CfpConfig.ENV_ACCOUNT_KEY).isEmpty()) {
      System.out.println(
          "(Export ACCOUNT_HOST and ACCOUNT_KEY to also see container document counts.)");
      return;
    }

    try (CosmosClient client = CosmosClients.sync()) {
      var database = client.getDatabase(CfpConfig.databaseName());
      System.out.printf(
          "Container '%s': %d documents%n",
          CfpConfig.containerName(), count(database, CfpConfig.containerName()));
      System.out.printf(
          "Container '%s': %d leases%n",
          CfpConfig.leaseContainerName(), count(database, CfpConfig.leaseContainerName()));
    }
  }

  private long count(com.azure.cosmos.CosmosDatabase database, String containerName) {
    return database
        .getContainer(containerName)
        .queryItems(
            "SELECT VALUE COUNT(1) FROM c",
            new com.azure.cosmos.models.CosmosQueryRequestOptions(),
            Long.class)
        .stream()
        .findFirst()
        .orElse(0L);
  }
}
