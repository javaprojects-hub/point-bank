package com.pointbank.bankplatform.repository;

import java.util.UUID;

import com.pointbank.bankplatform.entity.ConsumerEventProcessing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumerEventProcessingRepository extends JpaRepository<ConsumerEventProcessing, UUID> {

    boolean existsByEventId(String eventId);

    long deleteAllByEventId(String eventId);
}
