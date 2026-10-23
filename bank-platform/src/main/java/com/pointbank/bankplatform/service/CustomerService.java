package com.pointbank.bankplatform.service;

import java.util.List;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
import com.pointbank.bankplatform.dto.KycDocumentSummaryResponse;
import com.pointbank.bankplatform.dto.KycStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse getCustomerById(UUID id);

    CustomerResponse getCustomerByMobileNumber(String mobileNumber);

    CustomerResponse getCustomerByEmail(String email);

    CustomerResponse getCustomerByCustomerId(Long customerId);

    Page<CustomerResponse> getAllCustomers(Pageable pageable);

    CustomerResponse updateCustomer(UUID id, CustomerUpdateRequest request);

    KycDocumentResponse uploadKycDocument(Long customerId, KycDocumentType documentType, MultipartFile file);

    CustomerResponse verifyKyc(Long customerId, String reviewerId);

    KycStatusResponse getKycStatus(Long customerId);

    List<KycDocumentSummaryResponse> getKycDocuments(Long customerId);

    void deleteKycDocument(Long customerId, UUID documentId);

    KycStatusResponse rejectKyc(Long customerId, String reason, String reviewerId);

    KycDocumentResponse resubmitKyc(Long customerId, KycDocumentType documentType, MultipartFile file);
}
