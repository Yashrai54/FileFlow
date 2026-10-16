// service/TransferExecutor.java
package com.fileflow.service;

import com.fileflow.event.RunQueuedEvent;
import com.fileflow.models.RunStatus;
import com.fileflow.models.TransferJob;
import com.fileflow.models.TransferRun;
import com.fileflow.repository.TransferRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.time.Instant;

import org.hibernate.Hibernate;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransferExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransferExecutor.class);
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final TransferRunRepository runRepository;
    private final SftpTransferService sftpService;
    private final int maxAttempts;
    private final long initialDelayMs;

    private final TransactionTemplate transactionTemplate;


   public TransferExecutor(TransferRunRepository runRepository,
                        SftpTransferService sftpService,
                        TransactionTemplate transactionTemplate,
                        @Value("${fileflow.transfer.max-attempts:3}") int maxAttempts,
                        @Value("${fileflow.transfer.initial-delay-ms:2000}") long initialDelayMs) {
    this.runRepository = runRepository;
    this.sftpService = sftpService;
    this.transactionTemplate = transactionTemplate;
    this.maxAttempts = maxAttempts;
    this.initialDelayMs = initialDelayMs;
}
    /** Runs on the "transferExecutor" thread pool, only after the run row is committed. */
    @Async("transferTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onRunQueued(RunQueuedEvent event) {
        execute(event.runId());
    }

    void execute(Long runId) {
      TransferRun run = transactionTemplate.execute(status ->
            runRepository.findById(runId)
                    .map(r -> {
                        Hibernate.initialize(r.getJob()); // load the job while the session is open
                        return r;
                    })
                    .orElse(null));
    if (run == null) {
        log.warn("Run {} not found, skipping", runId);
        return;
    }
    TransferJob job = run.getJob();

        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run = runRepository.save(run);

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                TransferResult result = sftpService.transfer(job);
                finish(run, RunStatus.SUCCESS, result.bytesTransferred(),
                        "Transferred " + result.filesTransferred() + " file(s), "
                                + result.bytesTransferred() + " bytes, checksums verified (attempt "
                                + attempt + ")");
                return;
            } catch (IOException e) {
                log.warn("Run {} attempt {}/{} failed: {}", runId, attempt, maxAttempts, e.getMessage());
                if (attempt >= maxAttempts) {
                    finish(run, RunStatus.FAILED, null,
                            "Failed after " + attempt + " attempts: " + e.getMessage());
                    return;
                }
                long delay = initialDelayMs * (1L << (attempt - 1)); // 2s, 4s, 8s...
                if (!sleep(delay)) {
                    finish(run, RunStatus.FAILED, null, "Interrupted while waiting to retry");
                    return;
                }
            } catch (RuntimeException e) {
                // A broken job definition will not fix itself, so no retry.
                finish(run, RunStatus.FAILED, null, "Not retried: " + e.getMessage());
                return;
            }
        }
    }

    private void finish(TransferRun run, RunStatus status, Long bytes, String message) {
        run.setStatus(status);
        run.setFinishedAt(Instant.now());
        run.setBytesTransferred(bytes);
        run.setMessage(message != null && message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH)
                : message);
        runRepository.save(run);
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}