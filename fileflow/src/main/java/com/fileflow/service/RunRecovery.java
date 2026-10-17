// service/RunRecovery.java
package com.fileflow.service;

import com.fileflow.models.RunStatus;
import com.fileflow.models.TransferRun;
import com.fileflow.repository.TransferRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** After a crash or restart, runs still marked QUEUED/RUNNING can never finish, so close them out. */
@Component
public class RunRecovery {

    private static final Logger log = LoggerFactory.getLogger(RunRecovery.class);

    private final TransferRunRepository runRepository;

    public RunRecovery(TransferRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedRuns() {
        List<TransferRun> stuck = runRepository.findByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
        for (TransferRun run : stuck) {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            run.setMessage("Interrupted by application restart");
        }
        if (!stuck.isEmpty()) {
            log.warn("Marked {} interrupted run(s) as FAILED", stuck.size());
        }
    }
}