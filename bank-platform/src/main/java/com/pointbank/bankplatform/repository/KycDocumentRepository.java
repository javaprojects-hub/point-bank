package com.pointbank.bankplatform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findAllByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);

    List<KycDocument> findAllByCustomer_CustomerIdAndDocumentTypeOrderByVersionNumberDesc(
            Long customerId,
            KycDocumentType documentType
    );

    Optional<KycDocument> findByIdAndCustomer_CustomerId(UUID id, Long customerId);

    boolean existsByCustomer_CustomerIdAndCurrentVersionTrue(Long customerId);
}
