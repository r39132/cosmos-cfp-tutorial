package com.sanand.cfp.writer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Runtime reconfiguration payload. Both fields are optional so the UI can change one without having
 * to resend the other.
 */
public record WriterConfigRequest(
    @Min(10) @Max(600_000) Long intervalMillis, @Min(1) @Max(100_000) Integer documentCount) {}
