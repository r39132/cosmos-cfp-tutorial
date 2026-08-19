package com.sanand.cfp.writer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Spring Boot entry point for the upsert-driver web app. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WriterApplication {

  public static void main(String[] args) {
    SpringApplication.run(WriterApplication.class, args);
  }
}
