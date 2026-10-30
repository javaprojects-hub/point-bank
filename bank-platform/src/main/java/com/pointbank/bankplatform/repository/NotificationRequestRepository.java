package com.pointbank.bankplatform.repository;

import java.util.UUID;
import java.util.List;

import com.pointbank.bankplatform.entity.NotificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, UUID> {

    List<NotificationRequest> findAllByEventId(String eventId);

    long deleteAllByEventId(String eventId);
}
