package com.fileflow.repository;

import com.fileflow.models.TransferJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferJobRepository extends JpaRepository<TransferJob, Long> {
}
