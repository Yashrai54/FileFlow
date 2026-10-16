package com.fileflow.repository;

import com.fileflow.models.TransferRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransferRunRepository extends JpaRepository<TransferRun, Long> {
    
    List<TransferRun> findByTransferJobIdOrderByCreatedAtDesc(Long transferJobId);
    void deleteByTransferJobId(Long transferJobId);
}   
