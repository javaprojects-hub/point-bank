package com.pointbank.bankplatform.repository;

import java.util.UUID;

import com.pointbank.bankplatform.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    boolean existsByCustomer_Id(UUID customerId);
}
