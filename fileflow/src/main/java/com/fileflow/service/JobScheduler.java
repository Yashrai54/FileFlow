// service/JobScheduler.java
package com.fileflow.service;

import com.fileflow.event.JobChangedEvent;
import com.fileflow.models.JobStatus;
import com.fileflow.models.TransferJob;
import com.fileflow.repository.TransferJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Keeps one cron trigger per ACTIVE job that has a cron expression. */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final TaskScheduler taskScheduler;
    private final TransferJobRepository jobRepository;
    private final RunService runService;
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public JobScheduler(TaskScheduler taskScheduler,
                        TransferJobRepository jobRepository,
                        RunService runService) {
        this.taskScheduler = taskScheduler;
        this.jobRepository = jobRepository;
        this.runService = runService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAllOnStartup() {
        jobRepository.findAll().forEach(job -> refresh(job.getId()));
    }

    /** Fires only after the job change has been committed to the database. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onJobChanged(JobChangedEvent event) {
        refresh(event.jobId());
    }

    private void refresh(Long jobId) {
        ScheduledFuture<?> existing = scheduled.remove(jobId);
        if (existing != null) {
            existing.cancel(false);
        }
        jobRepository.findById(jobId)
                .filter(job -> job.getStatus() == JobStatus.ACTIVE)
                .filter(job -> job.getCronExpression() != null)
                .ifPresent(this::schedule);
    }

    private void schedule(TransferJob job) {
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> runScheduled(job.getId()),
                new CronTrigger(job.getCronExpression()));
        if (future != null) {
            scheduled.put(job.getId(), future);
            log.info("Scheduled job {} with cron '{}'", job.getId(), job.getCronExpression());
        }
    }

    private void runScheduled(Long jobId) {
        try {
            runService.trigger(jobId);
        } catch (RuntimeException e) {
            log.warn("Scheduled run of job {} skipped: {}", jobId, e.getMessage());
        }
    }
}