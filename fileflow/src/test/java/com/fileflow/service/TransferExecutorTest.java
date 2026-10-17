// TransferExecutorTest.java
package com.fileflow.service;

import com.fileflow.models.RunStatus;
import com.fileflow.models.TransferJob;
import com.fileflow.models.TransferRun;
import com.fileflow.repository.TransferRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TransferExecutorTest {

    @Mock
    private TransferRunRepository runRepository;
    @Mock
    private SftpTransferService sftpService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private TransferRun run;
    private TransferExecutor executor;

   @BeforeEach
void setUp() {
    run = new TransferRun(new TransferJob());
    lenient().when(transactionTemplate.execute(any())).thenReturn(run);
    lenient().when(runRepository.save(any(TransferRun.class))).thenAnswer(inv -> inv.getArgument(0));
    executor = new TransferExecutor(runRepository, sftpService, transactionTemplate, 3, 1L);
}

    @Test
    void succeedsOnFirstAttempt() throws Exception {
        when(sftpService.transfer(any())).thenReturn(new TransferResult(2, 100L));

        executor.execute(1L);

        assertEquals(RunStatus.SUCCESS, run.getStatus());
        assertEquals(100L, run.getBytesTransferred());
        assertTrue(run.getMessage().contains("2 file(s)"));
        assertTrue(run.getMessage().contains("attempt 1"));
        assertNotNull(run.getStartedAt());
        assertNotNull(run.getFinishedAt());
        verify(sftpService, times(1)).transfer(any());
    }

    @Test
    void retriesAfterNetworkErrorThenSucceeds() throws Exception {
        when(sftpService.transfer(any()))
                .thenThrow(new IOException("connection reset"))
                .thenThrow(new IOException("connection reset"))
                .thenReturn(new TransferResult(1, 10L));

        executor.execute(1L);

        assertEquals(RunStatus.SUCCESS, run.getStatus());
        assertTrue(run.getMessage().contains("attempt 3"));
        verify(sftpService, times(3)).transfer(any());
    }

    @Test
    void failsAfterMaxAttempts() throws Exception {
        when(sftpService.transfer(any())).thenThrow(new IOException("server down"));

        executor.execute(1L);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertTrue(run.getMessage().contains("Failed after 3 attempts"));
        assertTrue(run.getMessage().contains("server down"));
        verify(sftpService, times(3)).transfer(any());
    }

    @Test
    void doesNotRetryWhenJobDefinitionIsInvalid() throws Exception {
        when(sftpService.transfer(any()))
                .thenThrow(new IllegalArgumentException("Local directory not found"));

        executor.execute(1L);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertTrue(run.getMessage().startsWith("Not retried"));
        verify(sftpService, times(1)).transfer(any());
    }

    @Test
    void skipsWhenRunDoesNotExist() throws Exception {
        when(transactionTemplate.execute(any())).thenReturn(null);

        executor.execute(99L);

        verify(sftpService, never()).transfer(any());
    }
}