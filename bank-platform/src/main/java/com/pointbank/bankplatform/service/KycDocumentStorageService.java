package com.pointbank.bankplatform.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.exception.InvalidRequestException;
import com.pointbank.bankplatform.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KycDocumentStorageService {

    private static final long PDF_MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long PNG_MAX_SIZE_BYTES = 2L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final Path storageDirectory;

    public KycDocumentStorageService(
            @Value("${app.kyc.storage-directory:./var/kyc-documents}") Path storageDirectory
    ) {
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
    }

    public StoredKycDocument store(KycDocumentType documentType, MultipartFile file) {
        if (documentType == null || file == null || file.isEmpty()) {
            throw new InvalidRequestException("A KYC document type and non-empty file are required.");
        }

        String contentType = file.getContentType();
        String extension = validateFile(file, contentType);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the uploaded KYC document.", exception);
        }

        String storedFileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(storageDirectory);
            Files.write(
                    storageDirectory.resolve(storedFileName),
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the uploaded KYC document.", exception);
        }

        return new StoredKycDocument(storedFileName, contentType, content.length);
    }

    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(resolveStoredFile(storedFileName));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to remove the stored KYC document.", exception);
        }
    }

    public byte[] read(String storedFileName) {
        Path storedFile = resolveStoredFile(storedFileName);
        if (!Files.isRegularFile(storedFile, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(storedFile)) {
            throw new ResourceNotFoundException("KYC document content not found.");
        }

        try {
            return Files.readAllBytes(storedFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the stored KYC document.", exception);
        }
    }

    private Path resolveStoredFile(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            throw new InvalidRequestException("A stored KYC document filename is required.");
        }
        Path storedFile = storageDirectory.resolve(storedFileName).normalize();
        if (!storedFile.getParent().equals(storageDirectory)
                || !storedFile.getFileName().toString().equals(storedFileName)) {
            throw new InvalidRequestException("The stored KYC document filename is invalid.");
        }
        return storedFile;
    }

    private String validateFile(MultipartFile file, String contentType) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank() || contentType == null) {
            throw new InvalidRequestException("The KYC document must have a filename and content type.");
        }

        String normalizedFilename = originalFilename.toLowerCase(Locale.ROOT);
        long size = file.getSize();
        if ("application/pdf".equalsIgnoreCase(contentType)
                && normalizedFilename.endsWith(".pdf")
                && size <= PDF_MAX_SIZE_BYTES
                && hasSignature(file, PDF_SIGNATURE)) {
            return ".pdf";
        }
        if ("image/png".equalsIgnoreCase(contentType)
                && normalizedFilename.endsWith(".png")
                && size <= PNG_MAX_SIZE_BYTES
                && hasSignature(file, PNG_SIGNATURE)) {
            return ".png";
        }
        throw new InvalidRequestException(
                "KYC documents must be a valid PDF (maximum 5 MB) or PNG image (maximum 2 MB)."
        );
    }

    private boolean hasSignature(MultipartFile file, byte[] signature) {
        try (var inputStream = file.getInputStream()) {
            byte[] prefix = inputStream.readNBytes(signature.length);
            if (prefix.length != signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if (prefix[index] != signature[index]) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect the uploaded KYC document.", exception);
        }
    }

    public record StoredKycDocument(String storedFileName, String mediaType, long sizeBytes) {
    }
}
