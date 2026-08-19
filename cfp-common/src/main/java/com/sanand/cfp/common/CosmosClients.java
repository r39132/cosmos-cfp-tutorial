package com.sanand.cfp.common;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import java.util.List;

/**
 * Builds data-plane Cosmos clients with the settings this tutorial depends on.
 *
 * <p>Two choices matter for the Change Feed Processor:
 *
 * <ul>
 *   <li><b>Session consistency</b> matches how the account is provisioned by {@code cfp-admin}.
 *   <li><b>Preferred regions</b> pin the client to one write region. On a multi-write account this
 *       is what makes "which region am I reading the change feed from" an answerable question.
 * </ul>
 *
 * <p>Clients are expensive and thread-safe: build one per process and reuse it.
 */
public final class CosmosClients {

  private CosmosClients() {}

  /** Shared builder configuration used by both the sync and async factory methods. */
  private static CosmosClientBuilder builder() {
    return new CosmosClientBuilder()
        .endpoint(CfpConfig.accountHost())
        .key(CfpConfig.accountKey())
        .preferredRegions(List.of(CfpConfig.preferredRegion()))
        .consistencyLevel(ConsistencyLevel.SESSION)
        .contentResponseOnWriteEnabled(false)
        .userAgentSuffix("cosmos-cfp-tutorial");
  }

  /** Sync client — used by the admin app and the writer. */
  public static CosmosClient sync() {
    return builder().buildClient();
  }

  /**
   * Async client — <b>required</b> by the Change Feed Processor, which is built on Reactor and only
   * accepts {@code CosmosAsyncContainer} handles.
   */
  public static CosmosAsyncClient async() {
    return builder().buildAsyncClient();
  }
}
