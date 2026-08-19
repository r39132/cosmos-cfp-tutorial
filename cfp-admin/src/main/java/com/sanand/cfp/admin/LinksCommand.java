package com.sanand.cfp.admin;

import com.sanand.cfp.common.CfpConfig;
import com.sanand.cfp.common.PortalLinks;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Prints deep links into the Azure Portal / Cosmos Data Explorer for this account. */
@Command(
    name = "links",
    mixinStandardHelpOptions = true,
    description = "Print Azure Portal and Data Explorer links for the tutorial account.")
final class LinksCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    printLinks(CfpConfig.subscriptionId(), CfpConfig.resourceGroup(), CfpConfig.accountName());
    return 0;
  }

  static void printLinks(String subscriptionId, String resourceGroup, String accountName) {
    System.out.println("Console links:");
    System.out.printf(
        "  Account overview   : %s%n",
        PortalLinks.account(subscriptionId, resourceGroup, accountName));
    System.out.printf(
        "  Data Explorer      : %s%n",
        PortalLinks.dataExplorer(subscriptionId, resourceGroup, accountName));
    System.out.printf(
        "  Replicate globally : %s%n",
        PortalLinks.globalReplication(subscriptionId, resourceGroup, accountName));
    System.out.printf(
        "  Standalone explorer: %s%n", PortalLinks.standaloneDataExplorer(accountName));
  }
}
