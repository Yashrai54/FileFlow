// JobServiceTest.java
package com.fileflow.service;

import com.fileflow.dto.JobRequest;
import com.fileflow.dto.JobResponse;
import com.fileflow.event.JobChangedEvent;
import com.fileflow.exception.ResourceNotFoundException;
import com.fileflow.models.Direction;
import com.fileflow.models.JobStatus;
import com.fileflow.models.TransferJob;
import com.fileflow.repository.TransferJobRepository;
import com.fileflow.repository.TransferRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private TransferJobRepository jobRepository;
    @Mock
    private TransferRunRepository runRepository;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private JobService jobService;

    private JobRequest request(Integer port, String cron) {
        return new JobRequest("Invoice pull", "sftp.example.com", port, "svc",
                "/outbound", "/data/in", Direction.INBOUND, cron);
    }

    @Test
    void createUsesDefaultPortAndPublishesEvent() {
        when(jobRepository.save(any(TransferJob.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = jobService.create(request(null, null));

        assertEquals(22, response.port());
        assertEquals(JobStatus.ACTIVE, response.status());
        verify(events).publishEvent(any(JobChangedEvent.class));
    }

    @Test
    void createKeepsExplicitPortAndTreatsBlankCronAsNone() {
        when(jobRepository.save(any(TransferJob.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = jobService.create(request(2222, "   "));

        assertEquals(2222, response.port());
        assertNull(response.cronExpression());
    }

    @Test
    void createRejectsInvalidCron() {
        assertThrows(IllegalArgumentException.class,
                () -> jobService.create(request(null, "banana")));

        verify(jobRepository, never()).save(any());
        verify(events, never()).publishEvent(any(JobChangedEvent.class));
    }

    @Test
    void getThrowsWhenJobMissing() {
        when(jobRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> jobService.get(5L));
    }

    @Test
    void pauseAndResumeChangeStatus() {
        TransferJob job = new TransferJob();
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertEquals(JobStatus.PAUSED, jobService.pause(1L).status());
        assertEquals(JobStatus.ACTIVE, jobService.resume(1L).status());
    }
}