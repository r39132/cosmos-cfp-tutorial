package com.sanand.cfp.writer;

import com.sanand.cfp.common.CfpConfig;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST surface behind the single-page control panel served from {@code static/index.html}. */
@RestController
@RequestMapping("/api/writer")
class WriterController {

  private final UpsertService upsertService;

  WriterController(UpsertService upsertService) {
    this.upsertService = upsertService;
  }

  @GetMapping("/status")
  WriterStatus status() {
    return upsertService.status();
  }

  @GetMapping("/target")
  Map<String, String> target() {
    return Map.of(
        "endpoint", CfpConfig.accountHost(),
        "database", CfpConfig.databaseName(),
        "container", CfpConfig.containerName(),
        "preferredRegion", CfpConfig.preferredRegion());
  }

  @PostMapping("/start")
  WriterStatus start() {
    upsertService.start();
    return upsertService.status();
  }

  @PostMapping("/stop")
  WriterStatus stop() {
    upsertService.stop();
    return upsertService.status();
  }

  @PostMapping("/reset")
  WriterStatus reset() {
    upsertService.resetCounters();
    return upsertService.status();
  }

  @PutMapping("/config")
  ResponseEntity<WriterStatus> configure(@Valid @RequestBody WriterConfigRequest request) {
    upsertService.reconfigure(request.intervalMillis(), request.documentCount());
    return ResponseEntity.ok(upsertService.status());
  }
}
