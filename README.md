# Cosmos DB Change Feed Processor — Java Tutorial

A hands-on, runnable tutorial for the **Azure Cosmos DB Java V4 SDK** Change Feed Processor (CFP),
built on Java 17 and Maven.

Part 1 covers **Incremental CFP** (the mode the SDK calls *LatestVersion*): you see the newest
version of every changed document, once, in partition order.

## The three apps

| Module | What it is | What it does |
|---|---|---|
| `cfp-admin` | picocli CLI | Provisions and tears down the account, database and containers. Prints Azure Portal links. |
| `cfp-writer` | Spring Boot web app | A control panel to start/stop a steady stream of upserts, with adjustable rate and document count. |
| `cfp-reader` | picocli CLI | Runs the Change Feed Processor and prints one document per line, with a running count every 10. |
| `cfp-common` | library | Shared config, the `SensorReading` model, and the Cosmos client factory. |

---

## 1. Prerequisites

* An Azure subscription with permission to create Cosmos DB accounts.
* JDK 17 (this repo pins it via `jenv` — see below).
* Maven 3.9+ (or just use the bundled `./mvnw`).
* Azure CLI, logged in: `az login`.

### jenv

The repo carries a `.java-version` file, so `jenv` selects the right JDK the moment you `cd` in.

```bash
jenv local 17.0.8.1
```

Confirm it took:

```bash
make jdk
```

The build also enforces this: `maven-enforcer-plugin` fails fast on anything outside `[17,18)`,
so a stray JDK 23 on your `PATH` produces a clear error rather than a confusing bytecode failure.

---

## 2. Configure

```bash
cp .env.example .env
```

Fill in `AZURE_SUBSCRIPTION_ID`, `AZURE_RESOURCE_GROUP` and a globally-unique
`COSMOS_ACCOUNT_NAME`, then load it:

```bash
set -a && source .env && set +a
```

`.env` is gitignored. Nothing in this repo ever reads a key from a file that is tracked by git.

---

## 3. Build

```bash
make build
```

This runs unit tests, checks formatting with `google-java-format`, and produces three runnable
jars.

---

## 4. Provision

```bash
make provision
```

This creates, idempotently:

* the resource group (if missing),
* a **multi-region Cosmos DB account** for the SQL (NoSQL) API with:
  * **Session** consistency,
  * **two write regions** — `South Central US` and `West US 2` — via
    `withMultipleWriteLocationsEnabled(true)`,
* database `cfpdb`,
* monitored container `sensor-readings`, partitioned on `/deviceId`,
* lease container `leases`, partitioned on **`/id`**.

> Account creation takes 5–10 minutes. Re-running `provision` is safe; each step is skipped when
> the resource already exists.

When it finishes it prints the `export` lines for `ACCOUNT_HOST` and `ACCOUNT_KEY` — paste those
into your `.env` — followed by the console links.

To use a single write region instead:

```bash
java -jar cfp-admin/target/cfp-admin.jar provision --single-write-region
```

### Console links

```bash
make links
```

Prints deep links to the account Overview blade, the **Data Explorer**, the **Replicate data
globally** blade (where you can see both write regions), and the standalone
`cosmos.azure.com` explorer.

### Check state

```bash
make info
```

Shows consistency, the multi-write flag, every read/write region, and live document counts.

---

## 5. Run the writer

```bash
make writer
```

Open <http://localhost:8080>.

| Control | Default | Meaning |
|---|---|---|
| Write interval | `2000` ms | One document every 2 seconds. |
| Document count | `50` | Size of the **working set** — the loop cycles through 50 ids. |

The document count is a working-set size, not a one-shot total. After the first 50 writes every
subsequent write is an *update* to an existing document — which is precisely what incremental
change feed is designed to show you.

Start, stop, and change the rate at any time; changing the interval restarts the schedule so the
new rate applies immediately. Defaults live in `cfp-writer/src/main/resources/application.yml`
under `cfp.writer`.

REST surface, if you would rather drive it from `curl`:

```bash
curl -s localhost:8080/api/writer/status
curl -s -XPOST localhost:8080/api/writer/start
curl -s -XPUT localhost:8080/api/writer/config \
  -H 'Content-Type: application/json' -d '{"intervalMillis":500,"documentCount":200}'
curl -s -XPOST localhost:8080/api/writer/stop
```

---

## 6. Run the reader

From now on (the default — only changes made after the reader starts):

```bash
make reader
```

From the beginning of the change feed:

```bash
java -jar cfp-reader/target/cfp-reader.jar run --start-from=beginning
```

From a specific instant:

```bash
java -jar cfp-reader/target/cfp-reader.jar run \
  --start-from=time --start-time=2026-08-19T14:00:00Z
```

Output is one document per line, with the running count every 10 documents:

```
id=reading-00000 deviceId=device-000  seq=0      tempC=21.44  status=OK        updatedAt=2026-08-19T18:02:11.912Z
id=reading-00001 deviceId=device-001  seq=1      tempC=28.07  status=WARN      updatedAt=2026-08-19T18:02:13.930Z
...
>>> running count: 10 documents
```

Add `--pretty` to print full JSON instead of the compact summary.

### The start position only applies once

`--start-from` is consulted **only when no lease exists yet**. Once the processor has checkpointed,
the stored continuation token wins and a restart resumes exactly where it left off — that is the
whole point of the lease container.

Two ways to actually force a replay:

```bash
# A: new lease namespace, leaves existing consumers untouched
java -jar cfp-reader/target/cfp-reader.jar run --start-from=beginning --lease-prefix=replay-1

# B: delete the leases
make leases-purge
```

Inspect the leases at any time:

```bash
make leases
```

### Scale out

Run the reader twice in two terminals with **different host names and the same lease prefix**:

```bash
java -jar cfp-reader/target/cfp-reader.jar run --host-name=reader-a
java -jar cfp-reader/target/cfp-reader.jar run --host-name=reader-b
```

The physical partitions are distributed between the two instances. On a small container there may
only be one partition, so one instance will sit idle until the container splits — that is expected,
not a bug.

---

## 7. Clean up

```bash
make teardown
```

Prompts for the account name before deleting. Add `--delete-resource-group` to remove the whole
group.

> Cosmos DB bills for provisioned throughput whether or not you are writing. Tear the account down
> when you are done.

---

## What to read next

`docs/01-incremental-cfp.md` walks through what actually happens under the hood: the lease
protocol, checkpointing, delivery semantics, and where incremental mode's limits bite.

## Project hygiene

| Concern | Tool |
|---|---|
| JDK selection | `jenv` + `.java-version`, enforced by `maven-enforcer-plugin` |
| Formatting | `spotless` with `google-java-format`, checked in the `verify` phase |
| Tests | JUnit 5 + AssertJ, offline only |
| Coverage | JaCoCo |
| CI | GitHub Actions (`.github/workflows/build.yml`) |
| Secrets | Environment variables only; `.env` gitignored |

```bash
make format   # fix formatting
make lint     # check formatting
make verify   # tests + format check + coverage
```
