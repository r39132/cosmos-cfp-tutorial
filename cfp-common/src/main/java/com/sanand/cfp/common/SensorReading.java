package com.sanand.cfp.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * The document the writer upserts and the reader observes on the change feed.
 *
 * <p>Kept deliberately small so a single change-feed record fits on one console line. {@code
 * deviceId} is the partition key (see {@link CfpConfig#PARTITION_KEY_PATH}).
 *
 * <p>Cosmos adds system properties ({@code _rid}, {@code _ts}, {@code _etag}, ...) to every stored
 * document; {@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps deserialization from
 * exploding on them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorReading {

  private String id;
  private String deviceId;
  private long sequence;
  private double temperatureC;
  private String status;
  private Instant updatedAt;

  public SensorReading() {
    // Required by Jackson.
  }

  public SensorReading(
      String id, String deviceId, long sequence, double temperatureC, String status) {
    this.id = id;
    this.deviceId = deviceId;
    this.sequence = sequence;
    this.temperatureC = temperatureC;
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public long getSequence() {
    return sequence;
  }

  public void setSequence(long sequence) {
    this.sequence = sequence;
  }

  public double getTemperatureC() {
    return temperatureC;
  }

  public void setTemperatureC(double temperatureC) {
    this.temperatureC = temperatureC;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public String toString() {
    return "SensorReading{id=%s, deviceId=%s, seq=%d, tempC=%.2f, status=%s, updatedAt=%s}"
        .formatted(id, deviceId, sequence, temperatureC, status, updatedAt);
  }
}
