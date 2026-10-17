package com.fileflow.repository;

import com.fileflow.models.RunStatus;
import com.fileflow.models.TransferRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransferRunRepository extends JpaRepository<TransferRun, Long> {
    
    List<TransferRun> findByTransferJobIdOrderByCreatedAtDesc(Long transferJobId);
    void deleteByTransferJobId(Long transferJobId);

    boolean existsByTransferJobIdAndStatusIn(Long transferJobId, Collection<RunStatus> statuses);
    boolean existsByTransferJobIdAndStatus(Long jobId, RunStatus status);
    List<TransferRun> findByStatusIn(Collection<RunStatus> statuses);

        @Query("select r from TransferRun r join fetch r.transferJob where r.id = :id")
    Optional<TransferRun> findByIdWithJob(@Param("id") Long id);
}   
