package com.sanand.cfp.admin;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point for the admin app.
 *
 * <p>Two lifecycle commands ({@code provision}, {@code teardown}) plus two read-only ones ({@code
 * info}, {@code links}). Everything it needs comes from environment variables — see {@code
 * .env.example}.
 */
@Command(
    name = "cfp-admin",
    mixinStandardHelpOptions = true,
    version = "cfp-admin 1.0.0",
    description = "Provision / tear down the Cosmos DB account used by the CFP tutorial.",
    subcommands = {
      ProvisionCommand.class,
      TeardownCommand.class,
      InfoCommand.class,
      LinksCommand.class
    })
public final class AdminCli implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new AdminCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }
}
