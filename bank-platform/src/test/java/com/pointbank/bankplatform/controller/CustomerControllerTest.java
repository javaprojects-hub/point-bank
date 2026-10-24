package com.pointbank.bankplatform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
import com.pointbank.bankplatform.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerControllerTest {

    private CustomerService customerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        customerService = mock(CustomerService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(customerService)).build();
    }

    @Test
    void createCustomerReturnsCreatedStatusAndCustomerLocation() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.createCustomer(any(CustomerRequest.class))).thenReturn(
                new CustomerResponse(
                        customerId, 123L, "Asha", null, "Rao", "Female",
                        LocalDate.of(1990, 1, 1), "9876543210", "asha@example.com", List.of(),
                        CustomerStatus.CREATED, KycStatus.CREATED
                )
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Asha",
                                  "lastName": "Rao",
                                  "gender": "Female",
                                  "dateOfBirth": "1990-01-01",
                                  "mobileNumber": "9876543210",
                                  "email": "asha@example.com",
                                  "password": "SecurePassword123",
                                  "addresses": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/customers/" + customerId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer created successfully."))
                .andExpect(jsonPath("$.data.id").value(customerId.toString()))
                .andExpect(jsonPath("$.data.customerId").value(123))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void uploadKycDocumentReturnsUploadedDocumentMetadata() throws Exception {
        Long customerId = 123L;
        UUID documentId = UUID.randomUUID();
        when(customerService.uploadKycDocument(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.eq(KycDocumentType.PAN),
                any()
        )).thenReturn(new KycDocumentResponse(
                documentId, KycDocumentType.PAN, "application/pdf", 100, null
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}
        );

        mockMvc.perform(multipart("/api/v1/customers/{customerId}/kyc/documents", customerId)
                        .file(file)
                        .param("documentType", "PAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.documentType").value("PAN"))
                .andExpect(jsonPath("$.data.mediaType").value("application/pdf"));
    }

    @Test
    void verifyKycUsesPublicCustomerId() throws Exception {
        Long customerId = 123L;
        UUID id = UUID.randomUUID();
        CustomerResponse verifiedCustomer = new CustomerResponse(
                id, customerId, "Asha", null, "Rao", "Female",
                LocalDate.of(1990, 1, 1), "9876543210", "asha@example.com", List.of(),
                CustomerStatus.VERIFIED, KycStatus.VERIFIED
        );
        when(customerService.verifyKyc(customerId, "reviewer")).thenReturn(verifiedCustomer);

        mockMvc.perform(post("/api/v1/customers/{customerId}/kyc/verify", customerId)
                        .principal(() -> "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").value(customerId))
                .andExpect(jsonPath("$.data.customerStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"));

        verify(customerService).verifyKyc(customerId, "reviewer");
    }
}
