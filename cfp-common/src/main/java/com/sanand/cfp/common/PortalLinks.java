package com.sanand.cfp.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Deep links into the Azure Portal for the resources this tutorial creates. */
public final class PortalLinks {

  private static final String PORTAL = "https://portal.azure.com/#@/resource";

  private PortalLinks() {}

  /** ARM resource blade for the account (Overview, Keys, Replicate data globally, ...). */
  public static String account(String subscriptionId, String resourceGroup, String accountName) {
    return PORTAL + accountResourceId(subscriptionId, resourceGroup, accountName) + "/overview";
  }

  /** The Data Explorer blade — where you can watch documents land as the writer runs. */
  public static String dataExplorer(
      String subscriptionId, String resourceGroup, String accountName) {
    return PORTAL + accountResourceId(subscriptionId, resourceGroup, accountName) + "/dataExplorer";
  }

  /** The "Replicate data globally" blade, which shows the two write regions. */
  public static String globalReplication(
      String subscriptionId, String resourceGroup, String accountName) {
    return PORTAL
        + accountResourceId(subscriptionId, resourceGroup, accountName)
        + "/globalReplication";
  }

  /** Standalone Data Explorer, usable without portal navigation. */
  public static String standaloneDataExplorer(String accountName) {
    return "https://cosmos.azure.com/?accountName="
        + URLEncoder.encode(accountName, StandardCharsets.UTF_8);
  }

  private static String accountResourceId(
      String subscriptionId, String resourceGroup, String accountName) {
    return "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.DocumentDB/databaseAccounts/%s"
        .formatted(subscriptionId, resourceGroup, accountName);
  }
}
