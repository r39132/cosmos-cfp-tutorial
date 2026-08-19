package com.sanand.cfp.writer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Startup defaults for the upsert loop, overridable at runtime from the UI.
 *
 * <p>{@code documentCount} is the size of the <b>working set</b>, not a total to write once: the
 * loop cycles through that many document ids and upserts them over and over. That is deliberate —
 * incremental change feed shows the <i>latest version</i> of a changed document, so a small
 * recycling working set makes update-vs-insert behaviour easy to observe.
 */
@Validated
@ConfigurationProperties(prefix = "cfp.writer")
public class WriterProperties {

  /** Milliseconds between upserts. Default: one document every 2 seconds. */
  @Min(10)
  @Max(600_000)
  private long intervalMillis = 2_000;

  /** Number of distinct documents cycled through. */
  @Min(1)
  @Max(100_000)
  private int documentCount = 50;

  /** Prefix for generated document ids and device ids. */
  private String deviceIdPrefix = "device";

  /** Number of distinct partition keys the working set is spread over. */
  @Min(1)
  private int deviceCount = 5;

  public long getIntervalMillis() {
    return intervalMillis;
  }

  public void setIntervalMillis(long intervalMillis) {
    this.intervalMillis = intervalMillis;
  }

  public int getDocumentCount() {
    return documentCount;
  }

  public void setDocumentCount(int documentCount) {
    this.documentCount = documentCount;
  }

  public String getDeviceIdPrefix() {
    return deviceIdPrefix;
  }

  public void setDeviceIdPrefix(String deviceIdPrefix) {
    this.deviceIdPrefix = deviceIdPrefix;
  }

  public int getDeviceCount() {
    return deviceCount;
  }

  public void setDeviceCount(int deviceCount) {
    this.deviceCount = deviceCount;
  }
}
