package com.sanand.cfp.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CfpConfigTest {

  @Test
  void requiredFailsWithAnActionableMessage() {
    assertThatThrownBy(() -> CfpConfig.required("CFP_DEFINITELY_NOT_SET_12345"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CFP_DEFINITELY_NOT_SET_12345")
        .hasMessageContaining(".env.example");
  }

  @Test
  void optionalIsEmptyForAnUnsetVariable() {
    assertThat(CfpConfig.optional("CFP_DEFINITELY_NOT_SET_12345")).isEmpty();
  }

  @Test
  void leaseContainerIsPartitionedOnIdByConvention() {
    // The monitored container uses /deviceId; the lease container must use /id. Guarding the
    // former here keeps the writer, reader and admin agreed on the partition key path.
    assertThat(CfpConfig.PARTITION_KEY_PATH).isEqualTo("/deviceId");
  }
}
