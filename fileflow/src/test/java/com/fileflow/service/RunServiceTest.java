
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunServiceTest {

    @Mock
    private TransferJobRepository jobRepository;

    @Mock
    private TransferRunRepository runRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private RunService runService;

   
@Test
void triggerQueuesRunAndPublishesEvent() {
    TransferJob job = new TransferJob();
    job.setStatus(JobStatus.ACTIVE);

    when(jobRepository.findById(1L))
            .thenReturn(Optional.of(job));

    when(runRepository.existsByTransferJobIdAndStatus(
            1L, RunStatus.RUNNING))
            .thenReturn(false);

   when(runRepository.save(any(TransferRun.class)))
        .thenAnswer(invocation -> {
            TransferRun run = invocation.getArgument(0);

            Field idField = TransferRun.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(run, 10L);

            return run;
        });
    RunResponse response = runService.trigger(1L);

    org.junit.jupiter.api.Assertions.assertNotNull(response);

    verify(runRepository).save(any(TransferRun.class));
    verify(events).publishEvent(
            any(RunQueuedEvent.class));
}

@Test
void triggerRejectsOverlappingRun() {
    TransferJob job = new TransferJob();
    job.setStatus(JobStatus.ACTIVE);

    when(jobRepository.findById(1L))
            .thenReturn(Optional.of(job));

    when(runRepository.existsByTransferJobIdAndStatus(
            1L, RunStatus.RUNNING))
            .thenReturn(true);

    assertThrows(
            IllegalStateException.class,
            () -> runService.trigger(1L)
    );

    verify(runRepository, never()).save(any());
    verify(events, never())
            .publishEvent(any(RunQueuedEvent.class));
}


    @Test
    void triggerRejectsPausedJob() {
        TransferJob job = new TransferJob();
        job.setStatus(JobStatus.PAUSED);

        when(jobRepository.findById(1L))
                .thenReturn(Optional.of(job));

        assertThrows(
                IllegalStateException.class,
                () -> runService.trigger(1L)
        );

        verify(runRepository, never()).save(any());
    }

    
    @Test
    void triggerThrowsWhenJobMissing() {
        when(jobRepository.findById(7L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> runService.trigger(7L)
        );
    }
}
