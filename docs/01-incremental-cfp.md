# Part 1 — Incremental Change Feed Processing

> Prerequisite: the three apps from the [README](../README.md) are provisioned and running.

## What the change feed is

Every Cosmos DB container maintains a persistent, ordered log of changes to its items. It is not a
separate system you enable — it is a projection of the same replicated log the write path already
maintains. Reading it costs RUs against the container, and it survives restarts because *you*
checkpoint your position, not the service.

Two modes exist in the V4 Java SDK:

| Mode | SDK method | What you get |
|---|---|---|
| **Incremental** / *LatestVersion* | `handleLatestVersionChanges` | The newest version of each changed document. |
| All versions and deletes | `handleAllVersionsAndDeletesChanges` | Every intermediate version, plus deletes. Requires continuous backup. |

This tutorial covers the first.

## Three properties of incremental mode that surprise people

**1. It is not an audit log.** If a document is updated five times between two polls, you see it
once, with the final values. The intermediate versions are gone. The `sequence` field in
`SensorReading` makes this visible: crank the writer down to a 50 ms interval and you will see
sequence numbers skip.

**2. Deletes are invisible.** A deleted document simply stops appearing. If deletes matter, the
standard workaround is a soft delete — set a `deleted: true` flag and a TTL — so the delete arrives
as an ordinary update.

**3. Ordering is per partition key, not global.** Within one logical partition, changes arrive in
the order they were committed. Across partitions there is no guarantee. Any logic that assumes a
global sequence is a bug waiting for a partition split.

## The four moving parts

Look at `RunCommand.call()` in `cfp-reader`:

```java
ChangeFeedProcessor processor = new ChangeFeedProcessorBuilder()
    .hostName(hostName)                          // 3
    .feedContainer(feedContainer)                // 1
    .leaseContainer(leaseContainer)              // 2
    .handleLatestVersionChanges(this::onChanges) // 4
    .options(options)
    .buildChangeFeedProcessor();
```

### 1. The feed container

The container being watched — `sensor-readings`. Nothing about it is special; any container has a
change feed.

### 2. The lease container

Where progress is stored: one lease document per **physical partition**, each holding a
continuation token. This is the only reason a restarted reader knows where to resume.

It **must be partitioned on `/id`**. Getting this wrong is the most common CFP setup failure, and
the error it produces is not obvious. `cfp-admin` creates it correctly:

```java
database.createContainerIfNotExists(
    new CosmosContainerProperties(leaseContainerName, "/id"),
    ThroughputProperties.createManualThroughput(leaseThroughput));
```

The lease container needs its own RUs. It is written on every checkpoint and every lease renewal,
so on a busy feed it is not free.

### 3. The host name

Identifies this consumer instance. Multiple processes sharing a lease prefix but using distinct
host names divide the partitions between themselves — that is the entire scale-out story. Two
processes with the *same* host name will fight over leases, so make it unique per instance.

### 4. The delegate

```java
private void onChanges(List<ChangeFeedProcessorItem> items) {
  for (ChangeFeedProcessorItem item : items) {
    JsonNode current = item.getCurrent();
    ...
  }
}
```

Three things to internalise:

* **Returning normally is the checkpoint.** The SDK advances the continuation token only after your
  delegate returns.
* **Throwing means redelivery.** The batch is retried. Delivery is therefore **at-least-once**, so
  your handler must be idempotent.
* **Slow handlers are lag.** The delegate runs on a Reactor thread and blocks the poll loop for that
  lease. Offload real work to your own executor if it is not fast.

## Where you start

`ChangeFeedProcessorOptions` decides the start position, and `buildOptions()` maps the CLI flag
onto it:

| `--start-from` | Option call | Meaning |
|---|---|---|
| `now` (default) | `setStartFromBeginning(false)` | Only changes after the processor starts. |
| `beginning` | `setStartFromBeginning(true)` | Everything the feed still holds. |
| `time` | `setStartTime(Instant)` | Changes at or after that instant. |

**All three are ignored once a lease exists.** The stored continuation always wins. This trips
everyone up at least once: you pass `--start-from=beginning`, see nothing replay, and conclude the
flag is broken. It is not — you already have leases.

```bash
make leases          # look at them
make leases-purge    # delete them, forcing a genuine fresh start
```

Or side-step the issue entirely with a fresh namespace:

```bash
java -jar cfp-reader/target/cfp-reader.jar run \
  --start-from=beginning --lease-prefix=replay-$(date +%s)
```

## Exercises

### A. Watch updates behave differently from inserts

1. Start the reader with `make reader`.
2. Start the writer with the defaults (2 s, 50 documents).
3. For the first 50 changes, every `id` is new. After that, `id`s repeat while `sequence` keeps
   climbing — the same documents, updated.

### B. Prove that batching collapses versions

1. Stop the reader.
2. Set the writer to a 50 ms interval and 5 documents. Let it run for a minute, then stop it.
3. Start the reader with a fresh lease prefix and `--start-from=beginning`.

You will get far fewer changes than writes, and the `sequence` values will jump. That is
incremental mode collapsing versions, exactly as designed.

### C. Scale out

Run two readers with different `--host-name` values and the same lease prefix. Watch `make leases`
to see the `Owner` field. On a single-partition container one instance stays idle — correct
behaviour, since a lease cannot be split.

### D. Measure lag

Stop the reader while the writer keeps running, wait a minute, then restart it. On shutdown the
reader prints per-lease `estimatedLag`, sourced from `ChangeFeedProcessor.getCurrentState()`. That
same call is what you would export to a metrics backend in production.

## Multi-region notes

The account is provisioned with two write regions and Session consistency. `CosmosClients` pins the
client to one of them:

```java
.preferredRegions(List.of(CfpConfig.preferredRegion()))
.consistencyLevel(ConsistencyLevel.SESSION)
```

Point the reader at the other region and observe the difference:

```bash
COSMOS_PREFERRED_REGION='West US 2' make reader
```

The change feed is served from the region the client is reading from, so replication latency
between the write regions shows up directly as extra lag. Under Session consistency a reader in a
different region than the writer has no session token in common with it — which is why the change
feed, not a query, is the right tool for cross-region propagation.

## What Part 1 does not cover

* All-versions-and-deletes mode (needs continuous backup).
* Pull-model change feed (`queryChangeFeed`), where you own the loop and the continuation token.
* Throughput control groups to cap what the reader consumes.
* Handling partition splits mid-stream.
