package com.sanand.cfp.admin;

import com.sanand.cfp.common.CfpConfig;
import java.util.Scanner;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Deletes the resources created by {@code provision}.
 *
 * <p>Destructive, so it prompts unless {@code --yes} is passed. Default scope is the Cosmos account
 * only; {@code --delete-resource-group} widens it to the whole group.
 */
@Command(
    name = "teardown",
    mixinStandardHelpOptions = true,
    description = "Delete the Cosmos account (and optionally the resource group).")
final class TeardownCommand implements Callable<Integer> {

  @Option(names = "--yes", description = "Skip the interactive confirmation prompt.")
  private boolean assumeYes;

  @Option(
      names = "--delete-resource-group",
      description = "Also delete the whole resource group, not just the Cosmos account.")
  private boolean deleteResourceGroup;

  @Override
  public Integer call() {
    String resourceGroup = CfpConfig.resourceGroup();
    String accountName = CfpConfig.accountName();

    String target =
        deleteResourceGroup
            ? "resource group '%s' AND EVERYTHING IN IT".formatted(resourceGroup)
            : "Cosmos account '%s' in resource group '%s'".formatted(accountName, resourceGroup);

    if (!assumeYes && !confirm(target)) {
      System.out.println("Aborted. Nothing was deleted.");
      return 1;
    }

    System.out.printf("[-] Deleting Cosmos account '%s' ...%n", accountName);
    AzureClients.cosmosManager()
        .databaseAccounts()
        .deleteByResourceGroup(resourceGroup, accountName);
    System.out.println("[-] Account deleted.");

    if (deleteResourceGroup) {
      System.out.printf("[-] Deleting resource group '%s' ...%n", resourceGroup);
      AzureClients.resourceManager().resourceGroups().beginDeleteByName(resourceGroup);
      System.out.println("[-] Resource group deletion started (runs asynchronously in Azure).");
    }
    return 0;
  }

  private boolean confirm(String target) {
    System.out.printf("About to delete %s.%n", target);
    System.out.print("Type the account name to confirm: ");
    try (Scanner scanner = new Scanner(System.in)) {
      return scanner.hasNextLine() && CfpConfig.accountName().equals(scanner.nextLine().trim());
    }
  }
}
