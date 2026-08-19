package com.sanand.cfp.reader;

/** Where a brand-new lease set should begin reading from. */
public enum StartPosition {

  /**
   * Replay everything the container still retains. Note this is "beginning of the change feed", not
   * literally beginning of time: incremental mode surfaces only the <i>latest</i> version of each
   * document, and deletes are not represented at all.
   */
  BEGINNING,

  /** Only changes that happen from process start onwards. This is the default. */
  NOW,

  /** Changes at or after an explicit instant, supplied with {@code --start-time}. */
  TIME;

  /**
   * Every one of these only applies when the lease container has no leases yet. Once leases exist,
   * the stored continuation wins and the processor resumes exactly where it left off — use {@code
   * --lease-prefix} (or {@code leases purge}) to get a clean start.
   */
  @Override
  public String toString() {
    return name().toLowerCase();
  }
}
