package com.pointbank.bankplatform.service;

import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
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

    CustomerResponse verifyKyc(UUID customerId);
}
