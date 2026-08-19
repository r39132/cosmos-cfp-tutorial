package com.sanand.cfp.writer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WriterPropertiesTest {

  @Test
  void defaultsMatchTheDocumentedOneDocumentEveryTwoSeconds() {
    WriterProperties properties = new WriterProperties();
    assertThat(properties.getIntervalMillis()).isEqualTo(2_000);
    assertThat(properties.getDocumentCount()).isEqualTo(50);
  }
}
