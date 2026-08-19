package com.sanand.cfp.reader;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.ChangeFeedProcessorBuilder;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.ChangeFeedProcessorItem;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.azure.cosmos.models.ChangeFeedProcessorState;
import com.fasterxml.jackson.databind.JsonNode;
import com.sanand.cfp.common.CfpConfig;
import com.sanand.cfp.common.CosmosClients;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Runs the Change Feed Processor in <b>incremental</b> (LatestVersion) mode.
 *
 * <p>The moving parts:
 *
 * <ol>
 *   <li><b>Feed container</b> — the one being watched.
 *   <li><b>Lease container</b> — where the processor checkpoints its position, one lease per
 *       physical partition. Must be partitioned on {@code /id}.
 *   <li><b>Host name</b> — identifies this consumer instance. Two processes sharing a lease prefix
 *       and using different host names split the partitions between them.
 *   <li><b>Delegate</b> — {@code handleLatestVersionChanges}. Returning normally checkpoints the
 *       batch; throwing makes the processor retry it, so the delivery guarantee is at-least-once.
 * </ol>
 */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Start the Change Feed Processor and stream changes to the console.")
final class RunCommand implements Callable<Integer> {

  /** How often the running count is echoed. */
  private static final int COUNT_EVERY = 10;

  @Option(
      names = {"-s", "--start-from"},
      description =
          "Where to begin when no lease exists yet: ${COMPLETION-CANDIDATES}. "
              + "(default: ${DEFAULT-VALUE})")
  private StartPosition startFrom = StartPosition.NOW;

  @Option(
      names = "--start-time",
      description = "ISO-8601 instant, e.g. 2026-08-19T14:00:00Z. Required with --start-from=time.")
  private String startTime;

  @Option(
      names = {"-n", "--host-name"},
      description = "Consumer instance name recorded in the leases (default: a random id).")
  private String hostName = "reader-" + UUID.randomUUID().toString().substring(0, 8);

  @Option(
      names = {"-p", "--lease-prefix"},
      description =
          "Namespaces the leases. Change it to replay from scratch without touching existing "
              + "consumers (default: ${DEFAULT-VALUE}).")
  private String leasePrefix = "cfp-tutorial";

  @Option(
      names = "--max-items",
      description = "Max documents handed to the delegate per batch (default: ${DEFAULT-VALUE}).")
  private int maxItems = 100;

  @Option(
      names = "--poll-delay-millis",
      description =
          "Wait between polls when a partition has no new changes (default: ${DEFAULT-VALUE}).")
  private long pollDelayMillis = 1_000;

  @Option(
      names = "--pretty",
      description = "Print the full JSON of each change instead of a compact one-line summary.")
  private boolean pretty;

  @Option(
      names = "--duration-seconds",
      description = "Stop automatically after N seconds. Default is to run until Ctrl-C.")
  private long durationSeconds;

  private final AtomicLong processed = new AtomicLong();

  @Override
  public Integer call() throws Exception {
    ChangeFeedProcessorOptions options = buildOptions();

    try (CosmosAsyncClient client = CosmosClients.async()) {
      CosmosAsyncDatabase database = client.getDatabase(CfpConfig.databaseName());
      CosmosAsyncContainer feedContainer = database.getContainer(CfpConfig.containerName());
      CosmosAsyncContainer leaseContainer = database.getContainer(CfpConfig.leaseContainerName());

      ChangeFeedProcessor processor =
          new ChangeFeedProcessorBuilder()
              .hostName(hostName)
              .feedContainer(feedContainer)
              .leaseContainer(leaseContainer)
              .handleLatestVersionChanges(this::onChanges)
              .options(options)
              .buildChangeFeedProcessor();

      printHeader();

      // start() returns a Mono that completes once leases are acquired; block so that a
      // failure (missing container, bad lease partition key) surfaces here rather than silently.
      processor.start().block(Duration.ofSeconds(60));

      CountDownLatch shutdown = new CountDownLatch(1);
      Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "cfp-reader-shutdown"));

      if (durationSeconds > 0) {
        shutdown.await(durationSeconds, java.util.concurrent.TimeUnit.SECONDS);
      } else {
        shutdown.await();
      }

      System.out.printf("%n-- stopping (%d documents read) --%n", processed.get());
      printLag(processor);
      processor.stop().block(Duration.ofSeconds(30));
    }
    return 0;
  }

  private ChangeFeedProcessorOptions buildOptions() {
    ChangeFeedProcessorOptions options =
        new ChangeFeedProcessorOptions()
            .setLeasePrefix(leasePrefix)
            .setMaxItemCount(maxItems)
            .setFeedPollDelay(Duration.ofMillis(pollDelayMillis));

    switch (startFrom) {
      case BEGINNING -> options.setStartFromBeginning(true);
      case NOW -> options.setStartFromBeginning(false);
      case TIME -> {
        if (startTime == null || startTime.isBlank()) {
          throw new IllegalArgumentException("--start-from=time requires --start-time");
        }
        options.setStartTime(Instant.parse(startTime));
      }
    }
    return options;
  }

  /**
   * The delegate. Called on a Reactor thread with a batch of changes for one lease.
   *
   * <p>Keep it fast and idempotent: the batch is checkpointed only after this returns, so slow work
   * here directly increases lag, and a crash mid-batch means the batch is redelivered.
   */
  private void onChanges(List<ChangeFeedProcessorItem> items) {
    for (ChangeFeedProcessorItem item : items) {
      JsonNode current = item.getCurrent();
      System.out.println(pretty ? current.toPrettyString() : summarize(current));

      long n = processed.incrementAndGet();
      if (n % COUNT_EVERY == 0) {
        System.out.printf(">>> running count: %d documents%n", n);
      }
    }
  }

  private String summarize(JsonNode doc) {
    return "id=%-14s deviceId=%-12s seq=%-6s tempC=%-6s status=%-9s updatedAt=%s"
        .formatted(
            text(doc, "id"),
            text(doc, "deviceId"),
            text(doc, "sequence"),
            text(doc, "temperatureC"),
            text(doc, "status"),
            text(doc, "updatedAt"));
  }

  private static String text(JsonNode doc, String field) {
    JsonNode node = doc.get(field);
    return node == null || node.isNull() ? "-" : node.asText();
  }

  private void printHeader() {
    System.out.println("Change Feed Processor - incremental (LatestVersion) mode");
    System.out.printf("  endpoint      : %s%n", CfpConfig.accountHost());
    System.out.printf(
        "  feed container: %s/%s%n", CfpConfig.databaseName(), CfpConfig.containerName());
    System.out.printf("  leases        : %s%n", CfpConfig.leaseContainerName());
    System.out.printf("  lease prefix  : %s%n", leasePrefix);
    System.out.printf("  host name     : %s%n", hostName);
    System.out.printf(
        "  start from    : %s%n", startFrom == StartPosition.TIME ? startTime : startFrom);
    System.out.println("  (existing leases override the start position; Ctrl-C to stop)");
    System.out.println("-----------------------------------------------------------");
  }

  private void printLag(ChangeFeedProcessor processor) {
    List<ChangeFeedProcessorState> state =
        processor.getCurrentState().block(Duration.ofSeconds(20));
    if (state == null || state.isEmpty()) {
      return;
    }
    System.out.println("Lease state at shutdown:");
    state.forEach(
        s ->
            System.out.printf(
                "  lease=%s host=%s estimatedLag=%d%n",
                s.getLeaseToken(), s.getHostName(), s.getEstimatedLag()));
  }
}
