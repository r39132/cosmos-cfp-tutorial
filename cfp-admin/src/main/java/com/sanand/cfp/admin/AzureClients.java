package com.sanand.cfp.admin;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.cosmos.CosmosManager;
import com.azure.resourcemanager.resources.ResourceManager;
import com.sanand.cfp.common.CfpConfig;

/**
 * Control-plane (ARM) client factory.
 *
 * <p>Authentication goes through {@link DefaultAzureCredentialBuilder}, which picks up — in order —
 * environment service-principal variables, a managed identity, and finally the Azure CLI. For local
 * work {@code az login} is all that is needed.
 *
 * <p>Note the split: ARM manages the <i>account</i>, while databases and containers are created
 * over the data plane. Doing it that way keeps the SQL-API resources reachable with the same key
 * the writer and reader use.
 */
final class AzureClients {

  private AzureClients() {}

  static AzureProfile profile() {
    return new AzureProfile(AzureEnvironment.AZURE);
  }

  static ResourceManager resourceManager() {
    return ResourceManager.authenticate(new DefaultAzureCredentialBuilder().build(), profile())
        .withSubscription(CfpConfig.subscriptionId());
  }

  static CosmosManager cosmosManager() {
    return CosmosManager.configure()
        .authenticate(
            new DefaultAzureCredentialBuilder().build(),
            new AzureProfile(null, CfpConfig.subscriptionId(), AzureEnvironment.AZURE));
  }
}
