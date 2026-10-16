package com.fileflow.dto;

import com.fileflow.models.Direction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record JobRequest (
    @NotBlank @Size(max = 100) String name,
        @NotBlank String host,
        @Min(1) @Max(65535) Integer port,
        @NotBlank String username,
        @NotBlank String remotePath,
        @NotBlank String localPath,
        @NotNull Direction direction,
        String cronExpression) {
}
