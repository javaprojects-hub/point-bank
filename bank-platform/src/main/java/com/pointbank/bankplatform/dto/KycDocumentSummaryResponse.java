package com.pointbank.bankplatform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;

public record KycDocumentSummaryResponse(
        UUID id,
        KycDocumentType documentType,
        String mediaType,
        long sizeBytes,
        int version,
        boolean currentVersion,
        LocalDateTime uploadedAt
) {
}
