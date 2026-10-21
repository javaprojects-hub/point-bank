package com.pointbank.bankplatform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;

public record KycDocumentResponse(
        UUID id,
        KycDocumentType documentType,
        String mediaType,
        long sizeBytes,
        LocalDateTime uploadedAt
) {
}
