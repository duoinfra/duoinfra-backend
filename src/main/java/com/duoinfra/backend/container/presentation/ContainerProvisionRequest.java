package com.duoinfra.backend.container.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ContainerProvisionRequest(
        @Schema(description = "CPU 코어 수", example = "1")
        @Min(1) @Max(8)
        int cpu,

        @Schema(description = "메모리 (MB)", example = "512")
        @Min(128) @Max(8192)
        int memory
) {}
