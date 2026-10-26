package com.pointbank.bankplatform.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
import com.pointbank.bankplatform.dto.KycDocumentSummaryResponse;
import com.pointbank.bankplatform.dto.KycStatusResponse;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerServiceImpl.class);

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

    private final PasswordEncoder passwordEncoder;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            CustomerMapper customerMapper,
            KycDocumentRepository kycDocumentRepository,
            KycDocumentStorageService kycDocumentStorageService,
            PasswordEncoder passwordEncoder
    ) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.kycDocumentRepository = kycDocumentRepository;
        this.kycDocumentStorageService = kycDocumentStorageService;
        this. passwordEncoder =  passwordEncoder;
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
        customer.setPassword(passwordEncoder.encode(request.password()));
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
            LOGGER.warn(
                    "Rejected KYC document upload because required input is missing: customerId={}, documentType={}, filePresent={}",
                    customerId,
                    documentType,
                    file != null
            );
            throw new InvalidRequestException("Customer ID, KYC document type, and file are required.");
        }

        LOGGER.debug("Looking up customer for KYC document upload: customerId={}", customerId);
        Customer customer = requireCustomerForUpdate(customerId);
        if (customer.getKycStatus() == KycStatus.VERIFIED) {
            LOGGER.warn("KYC document upload rejected because customer is already verified: customerId={}", customerId);
            throw new ResourceConflictException("KYC is already verified for this customer.");
        }
        if (customer.getKycStatus() == KycStatus.REJECTED) {
            throw new ResourceConflictException("Use the KYC resubmission endpoint after a rejection.");
        }
        List<KycDocument> versions = kycDocumentRepository
                .findAllByCustomer_CustomerIdAndDocumentTypeOrderByVersionNumberDesc(customerId, documentType);
        if (versions.stream().anyMatch(KycDocument::isCurrentVersion)) {
            throw new ResourceConflictException("A current document of this type has already been uploaded.");
        }

        int nextVersion = versions.isEmpty() ? 1 : versions.getFirst().getVersionNumber() + 1;
        KycDocument document = saveKycDocument(customer, documentType, file, nextVersion);

        customer.setKycStatus(KycStatus.IN_PROGRESS);
        LOGGER.info(
                "KYC document stored and customer status updated: customerId={}, documentId={}, kycStatus={}",
                customerId,
                document.getId(),
                customer.getKycStatus()
        );
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
    public CustomerResponse verifyKyc(Long customerId, String reviewerId) {
        Customer customer = requireCustomerForUpdate(customerId);
        requireReviewer(reviewerId);
        if (customer.getKycStatus() == KycStatus.VERIFIED) {
            LOGGER.info("KYC is already verified: customerId={}", customerId);
            return customerMapper.toResponse(customer);
        }
        if (!kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)) {
            LOGGER.warn("KYC verification rejected because no documents are uploaded: customerId={}", customerId);
            throw new InvalidRequestException("At least one KYC document must be uploaded before verification.");
        }
        if (customer.getKycStatus() != KycStatus.IN_PROGRESS) {
            LOGGER.warn(
                    "KYC verification rejected due to unexpected status: customerId={}, kycStatus={}",
                    customerId,
                    customer.getKycStatus()
            );
            throw new InvalidRequestException("KYC must be submitted for review before it can be verified.");
        }

        customer.setKycStatus(KycStatus.VERIFIED);
        customer.setCustomerStatus(CustomerStatus.VERIFIED);
        customer.setKycVerifiedAt(LocalDateTime.now());
        customer.setKycReviewedAt(customer.getKycVerifiedAt());
        customer.setKycReviewedBy(reviewerId);
        customer.setKycRejectionReason(null);
        LOGGER.info(
                "KYC verification completed: customerId={}, customerStatus={}, kycStatus={}",
                customerId,
                customer.getCustomerStatus(),
                customer.getKycStatus()
        );
        return customerMapper.toResponse(customer);
    }

    @Override
    public KycStatusResponse getKycStatus(Long customerId) {
        return toKycStatusResponse(requireCustomer(customerId));
    }

    @Override
    public List<KycDocumentSummaryResponse> getKycDocuments(Long customerId) {
        requireCustomer(customerId);
        return kycDocumentRepository.findAllByCustomer_CustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(document -> new KycDocumentSummaryResponse(
                        document.getId(),
                        document.getDocumentType(),
                        document.getMediaType(),
                        document.getSizeBytes(),
                        document.getVersionNumber(),
                        document.isCurrentVersion(),
                        document.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteKycDocument(Long customerId, UUID documentId) {
        Customer customer = requireCustomerForUpdate(customerId);
        if (documentId == null) {
            throw new InvalidRequestException("KYC document ID is required.");
        }
        if (customer.getKycStatus() == KycStatus.VERIFIED || customer.getKycStatus() == KycStatus.REJECTED) {
            throw new ResourceConflictException("Documents cannot be deleted after a KYC review decision.");
        }

        KycDocument document = kycDocumentRepository.findByIdAndCustomer_CustomerId(documentId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC document not found."));
        List<KycDocument> versions = kycDocumentRepository
                .findAllByCustomer_CustomerIdAndDocumentTypeOrderByVersionNumberDesc(
                        customerId, document.getDocumentType()
                );
        if (document.isCurrentVersion()) {
            versions.stream()
                    .filter(version -> !version.getId().equals(documentId))
                    .findFirst()
                    .ifPresent(previousVersion -> {
                        previousVersion.setCurrentVersion(true);
                        kycDocumentRepository.save(previousVersion);
                    });
        }

        kycDocumentRepository.delete(document);
        if (!kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)
                && customer.getKycStatus() == KycStatus.IN_PROGRESS) {
            customer.setKycStatus(KycStatus.CREATED);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStoredDocument(document.getStoredFileName(), customerId);
                }
            });
        } else {
            deleteStoredDocument(document.getStoredFileName(), customerId);
        }
        LOGGER.info("KYC document deleted: customerId={}, documentId={}", customerId, documentId);
    }

    @Override
    @Transactional
    public KycStatusResponse rejectKyc(Long customerId, String reason, String reviewerId) {
        Customer customer = requireCustomerForUpdate(customerId);
        requireReviewer(reviewerId);
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new InvalidRequestException("A rejection reason of at most 500 characters is required.");
        }
        if (customer.getKycStatus() != KycStatus.IN_PROGRESS) {
            throw new InvalidRequestException("Only KYC submissions in progress can be rejected.");
        }
        if (!kycDocumentRepository.existsByCustomer_CustomerIdAndCurrentVersionTrue(customerId)) {
            throw new InvalidRequestException("At least one current KYC document is required for review.");
        }

        customer.setKycStatus(KycStatus.REJECTED);
        customer.setKycReviewedAt(LocalDateTime.now());
        customer.setKycReviewedBy(reviewerId);
        customer.setKycRejectionReason(reason.trim());
        LOGGER.info("KYC rejected: customerId={}, reviewerId={}", customerId, reviewerId);
        return toKycStatusResponse(customer);
    }

    @Override
    @Transactional
    public KycDocumentResponse resubmitKyc(Long customerId, KycDocumentType documentType, MultipartFile file) {
        Customer customer = requireCustomerForUpdate(customerId);
        if (customer.getKycStatus() != KycStatus.REJECTED) {
            throw new InvalidRequestException("KYC documents can only be resubmitted after rejection.");
        }
        if (documentType == null || file == null) {
            throw new InvalidRequestException("KYC document type and file are required.");
        }

        List<KycDocument> versions = kycDocumentRepository
                .findAllByCustomer_CustomerIdAndDocumentTypeOrderByVersionNumberDesc(customerId, documentType);
        int nextVersion = versions.isEmpty() ? 1 : versions.getFirst().getVersionNumber() + 1;
        versions.stream()
                .filter(KycDocument::isCurrentVersion)
                .forEach(previousVersion -> {
                    previousVersion.setCurrentVersion(false);
                    kycDocumentRepository.save(previousVersion);
                });
        KycDocument document = saveKycDocument(customer, documentType, file, nextVersion);

        customer.setKycStatus(KycStatus.IN_PROGRESS);
        LOGGER.info(
                "KYC resubmitted: customerId={}, documentType={}, version={}",
                customerId,
                documentType,
                nextVersion
        );
        return toKycDocumentResponse(document);
    }

    private KycDocument saveKycDocument(
            Customer customer,
            KycDocumentType documentType,
            MultipartFile file,
            int version
    ) {
        Long customerId = customer.getCustomerId();
        KycDocumentStorageService.StoredKycDocument storedDocument;
        try {
            storedDocument = kycDocumentStorageService.store(documentType, file);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to store KYC document: customerId={}, documentType={}",
                    customerId,
                    documentType,
                    exception
            );
            throw exception;
        }

        KycDocument document = new KycDocument();
        document.setCustomer(customer);
        document.setDocumentType(documentType);
        document.setStoredFileName(storedDocument.storedFileName());
        document.setMediaType(storedDocument.mediaType());
        document.setSizeBytes(storedDocument.sizeBytes());
        document.setVersionNumber(version);
        try {
            return kycDocumentRepository.save(document);
        } catch (DataAccessException exception) {
            LOGGER.error(
                    "Failed to save KYC document metadata: customerId={}, documentType={}",
                    customerId,
                    documentType,
                    exception
            );
            try {
                kycDocumentStorageService.delete(storedDocument.storedFileName());
            } catch (IllegalStateException cleanupException) {
                LOGGER.error(
                        "Failed to clean up stored KYC document after metadata save failure: customerId={}",
                        customerId,
                        cleanupException
                );
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private Customer requireCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new InvalidRequestException("A positive customer ID is required.");
        }
        return customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    private Customer requireCustomerForUpdate(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new InvalidRequestException("A positive customer ID is required.");
        }
        return customerRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> {
                    LOGGER.warn("Customer not found for KYC request: customerId={}", customerId);
                    return new ResourceNotFoundException("Customer not found.");
                });
    }

    private void requireReviewer(String reviewerId) {
        if (reviewerId == null || reviewerId.isBlank()) {
            throw new InvalidRequestException("An authenticated KYC reviewer is required.");
        }
    }

    private KycStatusResponse toKycStatusResponse(Customer customer) {
        return new KycStatusResponse(
                customer.getCustomerId(),
                customer.getKycStatus(),
                customer.getKycVerifiedAt(),
                customer.getKycReviewedAt(),
                customer.getKycReviewedBy(),
                customer.getKycRejectionReason()
        );
    }

    private KycDocumentResponse toKycDocumentResponse(KycDocument document) {
        return new KycDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getMediaType(),
                document.getSizeBytes(),
                document.getCreatedAt()
        );
    }

    private void deleteStoredDocument(String storedFileName, Long customerId) {
        try {
            kycDocumentStorageService.delete(storedFileName);
        } catch (IllegalStateException exception) {
            LOGGER.error(
                    "KYC document metadata was deleted but stored-file cleanup failed: customerId={}, storedFileName={}",
                    customerId,
                    storedFileName,
                    exception
            );
        }
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
