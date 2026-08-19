package com.sanand.cfp.reader;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.sanand.cfp.common.CfpConfig;
import com.sanand.cfp.common.CosmosClients;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Inspects or clears the lease documents.
 *
 * <p>Useful because {@code --start-from} is only consulted when no lease exists. Purging the leases
 * is how you actually force a replay with the same prefix.
 */
@Command(
    name = "leases",
    mixinStandardHelpOptions = true,
    description = "List or purge the Change Feed Processor lease documents.")
final class LeasesCommand implements Callable<Integer> {

  @Option(
      names = {"-p", "--lease-prefix"},
      description = "Only act on leases with this prefix (default: all leases).")
  private String leasePrefix;

  @Option(names = "--purge", description = "Delete the matching leases, forcing a fresh start.")
  private boolean purge;

  @Override
  public Integer call() {
    try (CosmosClient client = CosmosClients.sync()) {
      CosmosContainer leases =
          client.getDatabase(CfpConfig.databaseName()).getContainer(CfpConfig.leaseContainerName());

      List<JsonNode> matching = new ArrayList<>();
      leases
          .queryItems("SELECT * FROM c", new CosmosQueryRequestOptions(), JsonNode.class)
          .forEach(
              lease -> {
                String id = lease.path("id").asText();
                if (leasePrefix == null || id.startsWith(leasePrefix)) {
                  matching.add(lease);
                }
              });

      if (matching.isEmpty()) {
        System.out.println(
            "No leases found"
                + (leasePrefix == null ? "." : " with prefix '" + leasePrefix + "'."));
        return 0;
      }

      for (JsonNode lease : matching) {
        String id = lease.path("id").asText();
        System.out.printf(
            "%-60s owner=%-24s lease=%s%n",
            id, lease.path("Owner").asText("-"), lease.path("LeaseToken").asText("-"));
        if (purge) {
          leases.deleteItem(id, new PartitionKey(id), null);
        }
      }

      System.out.printf("%n%d lease(s) %s.%n", matching.size(), purge ? "deleted" : "listed");
      return 0;
    }
  }
}
