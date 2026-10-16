package com.fileflow.service;

import com.fileflow.dto.RunResponse;
import com.fileflow.event.RunQueuedEvent;
import com.fileflow.exception.ResourceNotFoundException;
import com.fileflow.models.JobStatus;
import com.fileflow.models.RunStatus;
import com.fileflow.models.TransferJob;
import com.fileflow.models.TransferRun;
import com.fileflow.repository.TransferJobRepository;
import com.fileflow.repository.TransferRunRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RunService {
    private final TransferJobRepository jobRepository;
    private final TransferRunRepository runRepository;
        private final ApplicationEventPublisher events;


    public RunService(TransferJobRepository jobRepository,
                      TransferRunRepository runRepository,
                      ApplicationEventPublisher events) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.events = events;
    }

    public RunResponse trigger(Long jobId) {
        TransferJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job " + jobId + " not found"));
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new IllegalStateException("Job " + jobId + " is paused and cannot be triggered");
        }
        TransferRun run = runRepository.save(new TransferRun(job));
        events.publishEvent(new RunQueuedEvent(run.getId()));
        return RunResponse.from(run, jobId);
    }

    @Transactional(readOnly = true)
    public List<RunResponse> listForJob(Long jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Job " + jobId + " not found");
        }
        return runRepository.findByTransferJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(run -> RunResponse.from(run, jobId))
                .toList();
    }

}
