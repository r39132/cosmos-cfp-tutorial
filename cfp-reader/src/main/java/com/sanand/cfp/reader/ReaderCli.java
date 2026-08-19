package com.sanand.cfp.reader;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point for the change-feed reader. */
@Command(
    name = "cfp-reader",
    mixinStandardHelpOptions = true,
    version = "cfp-reader 1.0.0",
    description =
        "Read the Cosmos DB change feed with the Change Feed Processor (incremental / "
            + "LatestVersion mode) and print one document per line.",
    subcommands = {RunCommand.class, LeasesCommand.class})
public final class ReaderCli implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    System.exit(
        new CommandLine(new ReaderCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
  }
}
