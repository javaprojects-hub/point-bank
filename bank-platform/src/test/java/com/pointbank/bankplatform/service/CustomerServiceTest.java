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
        UUID id = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerStatus(CustomerStatus.CREATED);
        customer.setKycStatus(KycStatus.CREATED);
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}
        );
        KycDocumentStorageService.StoredKycDocument storedDocument =
                new KycDocumentStorageService.StoredKycDocument("uuid.pdf", "application/pdf", 5);
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(kycDocumentStorageService.store(KycDocumentType.PAN, file)).thenReturn(storedDocument);
        when(kycDocumentRepository.save(any(KycDocument.class))).thenAnswer(invocation -> {
            KycDocument document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });

        customerService.uploadKycDocument(id, KycDocumentType.PAN, file);

        assertEquals(CustomerStatus.CREATED, customer.getCustomerStatus());
        assertEquals(KycStatus.IN_PROGRESS, customer.getKycStatus());
        verify(kycDocumentRepository).save(any(KycDocument.class));
    }

    @Test
    void verifyingKycRequiresAtLeastOneUploadedDocument() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setKycStatus(KycStatus.CREATED);
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.existsByCustomer_Id(id)).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> customerService.verifyKyc(id));
        assertEquals(KycStatus.CREATED, customer.getKycStatus());
    }

    @Test
    void verifyingSubmittedKycMarksCustomerAndKycVerified() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setKycStatus(KycStatus.IN_PROGRESS);
        CustomerResponse response = new CustomerResponse(
                id, 123L, "Asha", null, "Rao", "Female",
                java.time.LocalDate.of(1990, 1, 1), "9876543210", "asha@example.com", List.of(),
                CustomerStatus.VERIFIED, KycStatus.VERIFIED
        );
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.existsByCustomer_Id(id)).thenReturn(true);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = customerService.verifyKyc(id);

        assertEquals(response, result);
        assertEquals(CustomerStatus.VERIFIED, customer.getCustomerStatus());
        assertEquals(KycStatus.VERIFIED, customer.getKycStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(customer.getKycVerifiedAt());
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
