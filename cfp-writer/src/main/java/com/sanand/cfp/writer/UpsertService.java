package com.sanand.cfp.writer;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.sanand.cfp.common.SensorReading;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drives a steady, controllable stream of upserts into the monitored container.
 *
 * <p>A single-threaded scheduler owns the loop; every mutator is {@code synchronized} so that
 * start/stop/reconfigure from concurrent HTTP requests cannot interleave into a half-started state.
 * Changing the rate cancels the current schedule and starts a new one at the new period.
 */
@Service
public class UpsertService {

  private static final Logger log = LoggerFactory.getLogger(UpsertService.class);

  private static final List<String> STATUSES = List.of("OK", "WARN", "CRITICAL");

  private final CosmosContainer container;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "cfp-upsert-loop");
            t.setDaemon(true);
            return t;
          });

  private final AtomicLong totalUpserts = new AtomicLong();
  private final AtomicLong failures = new AtomicLong();
  private final AtomicLong cursor = new AtomicLong();

  private volatile long intervalMillis;
  private volatile int documentCount;
  private volatile String deviceIdPrefix;
  private volatile int deviceCount;
  private volatile String lastError;

  private ScheduledFuture<?> task;

  UpsertService(CosmosContainer container, WriterProperties properties) {
    this.container = container;
    this.intervalMillis = properties.getIntervalMillis();
    this.documentCount = properties.getDocumentCount();
    this.deviceIdPrefix = properties.getDeviceIdPrefix();
    this.deviceCount = properties.getDeviceCount();
  }

  public synchronized void start() {
    if (isRunning()) {
      return;
    }
    log.info("Starting upsert loop: every {} ms over {} documents", intervalMillis, documentCount);
    task =
        scheduler.scheduleAtFixedRate(this::upsertOnce, 0, intervalMillis, TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    if (task != null) {
      task.cancel(false);
      task = null;
      log.info("Stopped upsert loop after {} upserts", totalUpserts.get());
    }
  }

  /**
   * Applies new settings. If the loop is running it is restarted so the new interval takes effect
   * immediately rather than at some indeterminate future tick.
   */
  public synchronized void reconfigure(Long newIntervalMillis, Integer newDocumentCount) {
    boolean wasRunning = isRunning();
    stop();
    if (newIntervalMillis != null) {
      this.intervalMillis = newIntervalMillis;
    }
    if (newDocumentCount != null) {
      this.documentCount = newDocumentCount;
    }
    if (wasRunning) {
      start();
    }
  }

  /** Resets the counters. Does not delete anything in Cosmos. */
  public synchronized void resetCounters() {
    totalUpserts.set(0);
    failures.set(0);
    cursor.set(0);
    lastError = null;
  }

  public synchronized boolean isRunning() {
    return task != null && !task.isCancelled();
  }

  public WriterStatus status() {
    return new WriterStatus(
        isRunning(),
        intervalMillis,
        documentCount,
        totalUpserts.get(),
        failures.get(),
        cursor.get() % Math.max(documentCount, 1),
        lastError);
  }

  /**
   * Writes exactly one document per tick.
   *
   * <p>Any exception is swallowed and recorded: {@code scheduleAtFixedRate} silently cancels the
   * whole schedule if the task throws, which would look like a mysterious hang from the UI.
   */
  private void upsertOnce() {
    try {
      long n = cursor.getAndIncrement();
      int slot = (int) Math.floorMod(n, Math.max(documentCount, 1));
      String deviceId = "%s-%03d".formatted(deviceIdPrefix, slot % Math.max(deviceCount, 1));
      String id = "reading-%05d".formatted(slot);

      SensorReading reading =
          new SensorReading(
              id,
              deviceId,
              n,
              round(15.0 + ThreadLocalRandom.current().nextDouble() * 20.0),
              STATUSES.get(ThreadLocalRandom.current().nextInt(STATUSES.size())));

      container.upsertItem(reading, new PartitionKey(deviceId), new CosmosItemRequestOptions());
      totalUpserts.incrementAndGet();
    } catch (RuntimeException e) {
      failures.incrementAndGet();
      lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
      log.warn("Upsert failed ({} total failures)", failures.get(), e);
    }
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  @PreDestroy
  synchronized void shutdown() {
    stop();
    scheduler.shutdownNow();
    try {
      if (!scheduler.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
        log.warn("Upsert scheduler did not terminate within 5s");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
