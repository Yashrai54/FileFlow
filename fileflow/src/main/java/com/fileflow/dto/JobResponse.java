// dto/JobResponse.java
package com.fileflow.dto;

import com.fileflow.models.Direction;
import com.fileflow.models.JobStatus;
import com.fileflow.models.TransferJob;

import java.time.Instant;

public record JobResponse(
        Long id,
        String name,
        String host,
        int port,
        String username,
        String remotePath,
        String localPath,
        Direction direction,
        JobStatus status,
        String cronExpression,
        Instant createdAt) {

    public static JobResponse from(TransferJob job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getHost(),
                job.getPort(),
                job.getUsername(),
                job.getRemotePath(),
                job.getLocalPath(),
                job.getDirection(),
                job.getStatus(),
                job.getCronExpression(),
                job.getCreatedAt());
    }
}