package com.sanand.cfp.writer;

/** Snapshot of the upsert loop, serialized straight to JSON for the UI. */
public record WriterStatus(
    boolean running,
    long intervalMillis,
    int documentCount,
    long totalUpserts,
    long failures,
    long currentSlot,
    String lastError) {}
