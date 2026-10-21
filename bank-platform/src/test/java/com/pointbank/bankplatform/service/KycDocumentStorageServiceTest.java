package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class KycDocumentStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesSignatureValidatedPdfUnderGeneratedName() throws Exception {
        KycDocumentStorageService storageService = new KycDocumentStorageService(tempDirectory);
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-', '1'}
        );

        var storedDocument = storageService.store(KycDocumentType.PAN, file);

        assertEquals("application/pdf", storedDocument.mediaType());
        assertEquals(6, storedDocument.sizeBytes());
        org.junit.jupiter.api.Assertions.assertTrue(
                Files.exists(tempDirectory.resolve(storedDocument.storedFileName()))
        );
    }

    @Test
    void rejectsPdfLargerThanFiveMegabytes() {
        KycDocumentStorageService storageService = new KycDocumentStorageService(tempDirectory);
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        content[4] = '-';
        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", content);

        assertThrows(InvalidRequestException.class, () -> storageService.store(KycDocumentType.PAN, file));
    }

    @Test
    void rejectsPngLargerThanTwoMegabytes() {
        KycDocumentStorageService storageService = new KycDocumentStorageService(tempDirectory);
        byte[] content = new byte[2 * 1024 * 1024 + 1];
        byte[] signature = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(signature, 0, content, 0, signature.length);
        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.png", "image/png", content);

        assertThrows(InvalidRequestException.class, () -> storageService.store(KycDocumentType.AADHAAR, file));
    }

    @Test
    void rejectsMismatchedFileSignature() {
        KycDocumentStorageService storageService = new KycDocumentStorageService(tempDirectory);
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", "not a PDF".getBytes()
        );

        assertThrows(InvalidRequestException.class, () -> storageService.store(KycDocumentType.PAN, file));
    }
}
