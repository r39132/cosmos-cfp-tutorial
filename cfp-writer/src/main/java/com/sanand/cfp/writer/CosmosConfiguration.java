package com.sanand.cfp.writer;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.sanand.cfp.common.CfpConfig;
import com.sanand.cfp.common.CosmosClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a single, application-scoped Cosmos client.
 *
 * <p>{@code CosmosClient} is thread-safe and expensive to build (it warms up connections and
 * refreshes the region map). Spring closes the bean on shutdown via {@code destroyMethod}.
 */
@Configuration
class CosmosConfiguration {

  @Bean(destroyMethod = "close")
  CosmosClient cosmosClient() {
    return CosmosClients.sync();
  }

  @Bean
  CosmosContainer sensorContainer(CosmosClient client) {
    return client.getDatabase(CfpConfig.databaseName()).getContainer(CfpConfig.containerName());
  }
}
