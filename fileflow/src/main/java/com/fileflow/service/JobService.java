package com.fileflow.service;

import com.fileflow.dto.JobRequest;
import com.fileflow.dto.JobResponse;
import com.fileflow.exception.ResourceNotFoundException;
import com.fileflow.models.JobStatus;
import com.fileflow.models.TransferJob;
import com.fileflow.repository.TransferJobRepository;
import com.fileflow.repository.TransferRunRepository;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service 
@Transactional 
public class JobService {
    
    private static final int DEFAULT_SFTP_PORT = 22;

    private final TransferJobRepository transferJobRepository;
    private final TransferRunRepository transferRunRepository;

    public JobService(TransferJobRepository transferJobRepository, TransferRunRepository transferRunRepository) {
        this.transferJobRepository = transferJobRepository;
        this.transferRunRepository = transferRunRepository;
    }

    public JobResponse create(JobRequest jobRequest) {
        TransferJob job = new TransferJob();
        apply(job, jobRequest);
        return JobResponse.from(transferJobRepository.save(job));
    }
    @Transactional(readOnly = true)
    public List<JobResponse> list() {
        return transferJobRepository.findAll().stream()
                .map(JobResponse::from)
                .toList();
    }
    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return JobResponse.from(findOrThrow(id));
    }
    public JobResponse update(Long id, JobRequest request) {
        TransferJob job = findOrThrow(id);
        apply(job, request);
        return JobResponse.from(job);
    }

    public void delete(Long id) {
        TransferJob job = findOrThrow(id);
        transferRunRepository.deleteByTransferJobId(id);
        transferJobRepository.delete(job);
    }

    public JobResponse pause(Long id) {
        TransferJob job = findOrThrow(id);
        job.setStatus(JobStatus.PAUSED);
        return JobResponse.from(job);
    }

    public JobResponse resume(Long id) {
        TransferJob job = findOrThrow(id);
        job.setStatus(JobStatus.ACTIVE);
        return JobResponse.from(job);
    }

     private TransferJob findOrThrow(Long id) {
        return transferJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job " + id + " not found"));
    }
     private void apply(TransferJob job, JobRequest request) {
        String cron = request.cronExpression();
        if (cron != null && cron.isBlank()) {
            cron = null;
        }
        if (cron != null && !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Invalid cron expression: " + cron);
        }
        job.setName(request.name());
        job.setHost(request.host());
        job.setPort(request.port() != null ? request.port() : DEFAULT_SFTP_PORT);
        job.setUsername(request.username());
        job.setRemotePath(request.remotePath());
        job.setLocalPath(request.localPath());
        job.setDirection(request.direction());
        job.setCronExpression(cron);
    }
}
