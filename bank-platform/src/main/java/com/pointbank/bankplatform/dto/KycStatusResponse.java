package com.pointbank.bankplatform.dto;

import java.time.LocalDateTime;

import com.pointbank.bankplatform.constant.KycStatus;

public record KycStatusResponse(
        Long customerId,
        KycStatus status,
        LocalDateTime verifiedAt,
        LocalDateTime reviewedAt,
        String reviewedBy,
        String rejectionReason
) {
}
