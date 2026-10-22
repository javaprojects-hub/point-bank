package com.pointbank.bankplatform.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;
import org.springframework.web.multipart.MultipartFile;

import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.entity.KycDocument;
import com.pointbank.bankplatform.exception.InvalidRequestException;
import com.pointbank.bankplatform.exception.ResourceConflictException;
import com.pointbank.bankplatform.exception.ResourceNotFoundException;
import com.pointbank.bankplatform.mapper.CustomerMapper;
import com.pointbank.bankplatform.repository.CustomerRepository;
import com.pointbank.bankplatform.repository.KycDocumentRepository;

@Service
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CUSTOMER_ID_GENERATION_ATTEMPTS = 3;
    private static final String MOBILE_NUMBER_PATTERN = "^[6-9]\\d{9}$";
    private static final SecureRandom CUSTOMER_ID_RANDOM = new SecureRandom();
    private static final List<String> ALLOWED_SORT_PROPERTIES = List.of(
            "customerId", "firstName", "lastName", "email", "mobileNumber", "createdAt", "updatedAt"
    );

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final KycDocumentRepository kycDocumentRepository;
    private final KycDocumentStorageService kycDocumentStorageService;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            CustomerMapper customerMapper,
            KycDocumentRepository kycDocumentRepository,
            KycDocumentStorageService kycDocumentStorageService
    ) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.kycDocumentRepository = kycDocumentRepository;
        this.kycDocumentStorageService = kycDocumentStorageService;
    }

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Customer request is required.");
        }

        String email = normalizeEmail(request.email());
        String mobileNumber = normalizeMobileNumber(request.mobileNumber());
        ensureEmailAvailable(email, null);
        ensureMobileNumberAvailable(mobileNumber, null);

        Customer customer = customerMapper.toEntity(request);
        customer.setEmail(email);
        customer.setMobileNumber(mobileNumber);
        customer.setCustomerId(generateCustomerId());
        customer.setCustomerStatus(CustomerStatus.CREATED);
        customer.setKycStatus(KycStatus.CREATED);

        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse getCustomerById(UUID id) {
        if (id == null) {
            throw new InvalidRequestException("Customer ID is required.");
        }

        Customer customer = customerRepository.findWithAddressesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return customerMapper.toResponse(customer);
    }

    @Override
    public CustomerResponse getCustomerByMobileNumber(String mobileNumber) {
        String normalizedMobileNumber = normalizeMobileNumber(mobileNumber);
        Customer customer = customerRepository.findWithAddressesByMobileNumber(normalizedMobileNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return customerMapper.toResponse(customer);
    }

    @Override
    public CustomerResponse getCustomerByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        Customer customer = customerRepository.findWithAddressesByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return customerMapper.toResponse(customer);
    }

    @Override
    public CustomerResponse getCustomerByCustomerId(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new InvalidRequestException("Customer ID must be a positive number.");
        }
        Customer customer = customerRepository.findWithAddressesByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return customerMapper.toResponse(customer);
    }

    @Override
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        Pageable safePageable = normalizePageable(pageable);
        Page<Customer> customerPage = customerRepository.findAll(safePageable);
        if (customerPage.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, customerPage.getTotalElements());
        }

        List<UUID> customerIds = customerPage.getContent().stream()
                .map(Customer::getId)
                .toList();
        Map<UUID, Customer> customersWithAddresses = new HashMap<>();
        customerRepository.findAllWithAddressesByIdIn(customerIds)
                .forEach(customer -> customersWithAddresses.put(customer.getId(), customer));

        List<CustomerResponse> responses = customerPage.getContent().stream()
                .map(customer -> {
                    Customer customerWithAddresses = customersWithAddresses.get(customer.getId());
                    if (customerWithAddresses == null) {
                        throw new IllegalStateException("A customer from the requested page could not be loaded.");
                    }
                    return customerMapper.toResponse(customerWithAddresses);
                })
                .toList();

        return new PageImpl<>(responses, safePageable, customerPage.getTotalElements());
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerUpdateRequest request) {
        if (id == null || request == null) {
            throw new InvalidRequestException("Customer ID and update request are required.");
        }

        Customer customer = customerRepository.findWithAddressesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));

        String email = request.email() == null ? null : normalizeEmail(request.email());
        String mobileNumber = request.mobileNumber() == null ? null : normalizeMobileNumber(request.mobileNumber());

        if (email != null && !email.equalsIgnoreCase(customer.getEmail())) {
            ensureEmailAvailable(email, id);
        }
        if (mobileNumber != null && !mobileNumber.equals(customer.getMobileNumber())) {
            ensureMobileNumberAvailable(mobileNumber, id);
        }

        boolean emailChanged = email != null && !email.equalsIgnoreCase(customer.getEmail());
        boolean mobileNumberChanged = mobileNumber != null && !mobileNumber.equals(customer.getMobileNumber());
        customerMapper.updateEntity(request, customer);
        if (email != null) {
            customer.setEmail(email);
        }
        if (mobileNumber != null) {
            customer.setMobileNumber(mobileNumber);
        }
        if (mobileNumberChanged) {
            customer.setMobileNumberVerified(false);
        }
        if (emailChanged) {
            customer.setEmailVerified(false);
        }

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    public KycDocumentResponse uploadKycDocument(
            Long customerId,
            KycDocumentType documentType,
            MultipartFile file
    ) {
        if (customerId == null || documentType == null || file == null) {
            throw new InvalidRequestException("Customer ID, KYC document type, and file are required.");
        }

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getKycStatus() == KycStatus.VERIFIED) {
            throw new ResourceConflictException("KYC is already verified for this customer.");
        }

        KycDocumentStorageService.StoredKycDocument storedDocument =
                kycDocumentStorageService.store(documentType, file);
        KycDocument document = new KycDocument();
        document.setCustomer(customer);
        document.setDocumentType(documentType);
        document.setStoredFileName(storedDocument.storedFileName());
        document.setMediaType(storedDocument.mediaType());
        document.setSizeBytes(storedDocument.sizeBytes());
        try {
            kycDocumentRepository.save(document);
        } catch (DataAccessException exception) {
            try {
                kycDocumentStorageService.delete(storedDocument.storedFileName());
            } catch (IllegalStateException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        customer.setKycStatus(KycStatus.IN_PROGRESS);
        return new KycDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getMediaType(),
                document.getSizeBytes(),
                document.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public CustomerResponse verifyKyc(UUID customerId) {
        if (customerId == null) {
            throw new InvalidRequestException("Customer ID is required.");
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getKycStatus() == KycStatus.VERIFIED) {
            return customerMapper.toResponse(customer);
        }
        if (!kycDocumentRepository.existsByCustomer_Id(customerId)) {
            throw new InvalidRequestException("At least one KYC document must be uploaded before verification.");
        }
        if (customer.getKycStatus() != KycStatus.IN_PROGRESS) {
            throw new InvalidRequestException("KYC must be submitted for review before it can be verified.");
        }

        customer.setKycStatus(KycStatus.VERIFIED);
        customer.setCustomerStatus(CustomerStatus.VERIFIED);
        customer.setKycVerifiedAt(LocalDateTime.now());
        return customerMapper.toResponse(customer);
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }

        if (pageable.getSort().stream()
                .anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))) {
            throw new InvalidRequestException("Unsupported customer sort property.");
        }

        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), pageSize, pageable.getSort());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMobileNumber(String mobileNumber) {
        if (mobileNumber == null || !mobileNumber.trim().matches(MOBILE_NUMBER_PATTERN)) {
            throw new InvalidRequestException("Mobile number must be 10 digits and start with 6 through 9.");
        }
        return mobileNumber.trim();
    }

    private void ensureEmailAvailable(String email, UUID currentCustomerId) {
        customerRepository.findByEmailIgnoreCase(email)
                .filter(customer -> !customer.getId().equals(currentCustomerId))
                .ifPresent(customer -> {
                    throw new ResourceConflictException("Email address is already registered.");
                });
    }

    private void ensureMobileNumberAvailable(String mobileNumber, UUID currentCustomerId) {
        customerRepository.findByMobileNumber(mobileNumber)
                .filter(customer -> !customer.getId().equals(currentCustomerId))
                .ifPresent(customer -> {
                    throw new ResourceConflictException("Mobile number is already registered.");
                });
    }

   private Long generateCustomerId() {

    for (int attempts = 0 ; attempts < CUSTOMER_ID_GENERATION_ATTEMPTS; attempts++) {

        long customerId = 10000000L + CUSTOMER_ID_RANDOM.nextLong(99999999L);

        if (!customerRepository.existsByCustomerId(customerId)) {

        return customerId;
    }
}

    throw new IllegalStateException("Customer Id is already existed");
}
  
}

