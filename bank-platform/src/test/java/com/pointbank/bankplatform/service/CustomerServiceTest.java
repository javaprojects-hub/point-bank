package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.KycDocumentSummaryResponse;
import com.pointbank.bankplatform.dto.KycStatusResponse;
import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.entity.KycDocument;
import com.pointbank.bankplatform.exception.InvalidRequestException;
import com.pointbank.bankplatform.exception.ResourceNotFoundException;
import com.pointbank.bankplatform.mapper.CustomerMapper;
import com.pointbank.bankplatform.repository.CustomerRepository;
import com.pointbank.bankplatform.repository.KycDocumentRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @Mock
    private KycDocumentStorageService kycDocumentStorageService;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(
                customerRepository,
                customerMapper,
                kycDocumentRepository,
                kycDocumentStorageService
        );
    }

    @Test
    void createCustomerNormalizesUniqueFieldsAndSetsInitialStatuses() {
        CustomerRequest request = new CustomerRequest(
                "Asha", null, "Rao", "Female",
                java.time.LocalDate.of(1990, 1, 1),
                "9876543210", "ASHA@example.com", List.of()
        );
        Customer customer = new Customer();
        CustomerResponse response = new CustomerResponse(
                null, 123L, "Asha", null, "Rao", "Female",
                java.time.LocalDate.of(1990, 1, 1), "9876543210", "asha@example.com", List.of(),
                CustomerStatus.CREATED, KycStatus.CREATED
        );
        when(customerRepository.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.empty());
        when(customerRepository.findByMobileNumber("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.existsByCustomerId(anyLong())).thenReturn(false);
        when(customerMapper.toEntity(request)).thenReturn(customer);
        when(customerRepository.save(customer)).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = customerService.createCustomer(request);

        assertEquals(response, result);
        assertEquals("asha@example.com", customer.getEmail());
        assertEquals("9876543210", customer.getMobileNumber());
        assertEquals(CustomerStatus.CREATED, customer.getCustomerStatus());
        assertEquals(KycStatus.CREATED, customer.getKycStatus());
        verify(customerRepository).save(customer);
    }

    @Test
    void uploadingOneKycDocumentSubmitsCustomerForReview() {
        Long customerId = 123L;
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setCustomerStatus(CustomerStatus.CREATED);
        customer.setKycStatus(KycStatus.CREATED);
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}
        );
        KycDocumentStorageService.StoredKycDocument storedDocument =
                new KycDocumentStorageService.StoredKycDocument("uuid.pdf", "application/pdf", 5);
        when(customerRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentStorageService.store(KycDocumentType.PAN, file)).thenReturn(storedDocument);
        when(kycDocumentRepository.save(any(KycDocument.class))).thenAnswer(invocation -> {
            KycDocument document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });

        customerService.uploadKycDocument(customerId, KycDocumentType.PAN, file);

        assertEquals(CustomerStatus.CREATED, customer.getCustomerStatus());
        assertEquals(KycStatus.IN_PROGRESS, customer.getKycStatus());
        verify(kycDocumentRepository).save(any(KycDocument.class));
    }

    @Test
    void verifyingKycRequiresAtLeastOneUploadedDocument() {
        Long customerId = 123L;
        UUID id = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerId(customerId);
        customer.setKycStatus(KycStatus.CREATED);
        when(customerRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)).thenReturn(false);

        assertThrows(
                InvalidRequestException.class,
                () -> customerService.verifyKyc(customerId, "reviewer")
        );
        assertEquals(KycStatus.CREATED, customer.getKycStatus());
    }

    @Test
    void verifyingSubmittedKycMarksCustomerAndKycVerified() {
        Long customerId = 123L;
        UUID id = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerId(customerId);
        customer.setKycStatus(KycStatus.IN_PROGRESS);
        CustomerResponse response = new CustomerResponse(
                id, 123L, "Asha", null, "Rao", "Female",
                java.time.LocalDate.of(1990, 1, 1), "9876543210", "asha@example.com", List.of(),
                CustomerStatus.VERIFIED, KycStatus.VERIFIED
        );
        when(customerRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)).thenReturn(true);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = customerService.verifyKyc(customerId, "reviewer");

        assertEquals(response, result);
        assertEquals(CustomerStatus.VERIFIED, customer.getCustomerStatus());
        assertEquals(KycStatus.VERIFIED, customer.getKycStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(customer.getKycVerifiedAt());
    }

    @Test
    void rejectingKycPersistsReasonAndReviewer() {
        Long customerId = 123L;
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setKycStatus(KycStatus.IN_PROGRESS);
        when(customerRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)).thenReturn(true);

        KycStatusResponse response = customerService.rejectKyc(customerId, "Image is unreadable", "reviewer-1");

        assertEquals(KycStatus.REJECTED, customer.getKycStatus());
        assertEquals("Image is unreadable", customer.getKycRejectionReason());
        assertEquals("reviewer-1", customer.getKycReviewedBy());
        assertEquals("Image is unreadable", response.rejectionReason());
    }

    @Test
    void resubmittingKycAppendsVersionAndReactivatesReview() {
        Long customerId = 123L;
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setKycStatus(KycStatus.REJECTED);
        KycDocument previousVersion = new KycDocument();
        previousVersion.setId(UUID.randomUUID());
        previousVersion.setDocumentType(KycDocumentType.PAN);
        previousVersion.setVersionNumber(1);
        previousVersion.setCurrentVersion(true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}
        );
        when(customerRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.findAllByCustomer_CustomerIdAndDocumentTypeOrderByVersionNumberDesc(
                customerId, KycDocumentType.PAN
        )).thenReturn(List.of(previousVersion));
        when(kycDocumentStorageService.store(KycDocumentType.PAN, file)).thenReturn(
                new KycDocumentStorageService.StoredKycDocument("uuid-v2.pdf", "application/pdf", 5)
        );
        when(kycDocumentRepository.save(any(KycDocument.class))).thenAnswer(invocation -> {
            KycDocument document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });

        customerService.resubmitKyc(customerId, KycDocumentType.PAN, file);

        assertEquals(KycStatus.IN_PROGRESS, customer.getKycStatus());
        org.junit.jupiter.api.Assertions.assertFalse(previousVersion.isCurrentVersion());
        org.mockito.ArgumentCaptor<KycDocument> savedDocuments =
                org.mockito.ArgumentCaptor.forClass(KycDocument.class);
        verify(kycDocumentRepository, org.mockito.Mockito.times(2)).save(savedDocuments.capture());
        assertEquals(2, savedDocuments.getAllValues().getLast().getVersionNumber());
    }

    @Test
    void deletingDocumentBeforeVerificationRemovesMetadataAndFile() {
        Long customerId = 123L;
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setKycStatus(KycStatus.IN_PROGRESS);
        KycDocument document = new KycDocument();
        UUID documentId = UUID.randomUUID();
        document.setId(documentId);
        document.setCustomer(customer);
        document.setDocumentType(KycDocumentType.PAN);
        document.setStoredFileName("stored.pdf");
        document.setCurrentVersion(true);
        when(customerRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.findByIdAndCustomer_CustomerId(documentId, customerId))
                .thenReturn(Optional.of(document));
        when(kycDocumentRepository.findAllByCustomer_CustomerIdAndDocumentTypeOrderByVersionNumberDesc(
                customerId, KycDocumentType.PAN
        )).thenReturn(List.of(document));
        when(kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)).thenReturn(false);

        customerService.deleteKycDocument(customerId, documentId);

        assertEquals(KycStatus.CREATED, customer.getKycStatus());
        verify(kycDocumentRepository).delete(document);
        verify(kycDocumentStorageService).delete("stored.pdf");
    }

    @Test
    void retrievesKycStatusByPublicCustomerId() {
        Long customerId = 123L;
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setKycStatus(KycStatus.REJECTED);
        customer.setKycRejectionReason("Image is unreadable");
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));

        KycStatusResponse response = customerService.getKycStatus(customerId);

        assertEquals(customerId, response.customerId());
        assertEquals(KycStatus.REJECTED, response.status());
        assertEquals("Image is unreadable", response.rejectionReason());
    }

    @Test
    void listsKycDocumentVersionMetadataWithoutExposingStoredFileName() {
        Long customerId = 123L;
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        KycDocument document = new KycDocument();
        UUID documentId = UUID.randomUUID();
        document.setId(documentId);
        document.setCustomer(customer);
        document.setDocumentType(KycDocumentType.PAN);
        document.setStoredFileName("private-stored-name.pdf");
        document.setMediaType("application/pdf");
        document.setSizeBytes(1024);
        document.setVersionNumber(2);
        document.setCurrentVersion(true);
        when(customerRepository.findByCustomerId(customerId)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.findAllByCustomer_CustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(document));

        List<KycDocumentSummaryResponse> documents = customerService.getKycDocuments(customerId);

        assertEquals(1, documents.size());
        assertEquals(documentId, documents.getFirst().id());
        assertEquals(2, documents.getFirst().version());
        org.junit.jupiter.api.Assertions.assertTrue(documents.getFirst().currentVersion());
    }

    @Test
    void getCustomerByIdThrowsNotFoundWhenNoCustomerExists() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findWithAddressesById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.getCustomerById(id));
    }

    @Test
    void getAllCustomersLoadsAddressesInOneBatchForThePage() {
        UUID id = UUID.randomUUID();
        Customer pageCustomer = new Customer();
        pageCustomer.setId(id);
        Customer loadedCustomer = new Customer();
        loadedCustomer.setId(id);
        CustomerResponse response = new CustomerResponse(
                id, 123L, "Asha", null, "Rao", "Female",
                java.time.LocalDate.of(1990, 1, 1), "9876543210", "asha@example.com", List.of(),
                CustomerStatus.CREATED, KycStatus.CREATED
        );
        PageRequest pageable = PageRequest.of(0, 20);

        when(customerRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(pageCustomer), pageable, 1));
        when(customerRepository.findAllWithAddressesByIdIn(List.of(id)))
                .thenReturn(List.of(loadedCustomer));
        when(customerMapper.toResponse(loadedCustomer)).thenReturn(response);

        var result = customerService.getAllCustomers(pageable);

        assertEquals(List.of(response), result.getContent());
        verify(customerRepository).findAllWithAddressesByIdIn(eq(List.of(id)));
    }
}
