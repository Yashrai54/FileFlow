package com.fileflow.dto;

import com.fileflow.models.RunStatus;
import com.fileflow.models.TransferRun;

import java.time.Instant;

public record RunResponse(
        Long id,
        Long jobId,
        RunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long bytesTransferred,
        String message) {

    public static RunResponse from(TransferRun run, Long jobId) {
        return new RunResponse(
                run.getId(),
                jobId,
                run.getStatus(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getBytesTransferred(),
                run.getMessage());
    }
}
