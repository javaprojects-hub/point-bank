package com.pointbank.bankplatform.controller;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.dto.ApiResponseDto;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
import com.pointbank.bankplatform.dto.KycDocumentSummaryResponse;
import com.pointbank.bankplatform.dto.KycRejectionRequest;
import com.pointbank.bankplatform.dto.KycStatusResponse;
import com.pointbank.bankplatform.exception.ApiErrorResponse;
import com.pointbank.bankplatform.service.CustomerService;
import com.pointbank.bankplatform.service.CustomerIdentityService;
import com.pointbank.bankplatform.service.KycDocumentFileContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
@Validated
@Tag(name = "Customers", description = "Create, retrieve, list, and update customers.")
public class CustomerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;
    private final CustomerIdentityService customerIdentityService;

    public CustomerController(
            CustomerService customerService,
            CustomerIdentityService customerIdentityService
    ) {
        this.customerService = customerService;
        this.customerIdentityService = customerIdentityService;
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Customer created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email or mobile number is already registered",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponseDto<CustomerResponse>> createCustomer(
            @Valid @RequestBody CustomerRequest request
    ) {
        CustomerResponse customer = customerService.createCustomer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(customer.id())
                .toUri();
        return ResponseEntity.created(location)
                .body(ApiResponseDto.success("Customer created successfully.", customer));
    }

    @PostMapping(value = "/{customerId}/kyc/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PAN or Aadhaar document for KYC review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KYC document uploaded for review"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid document type or file",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "KYC has already been verified",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PreAuthorize("hasAnyRole('CUSTOMER', 'KYC_REVIEWER')")
    public ApiResponseDto<KycDocumentResponse> uploadKycDocument(
            @PathVariable @Positive Long customerId,
            @RequestParam KycDocumentType documentType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        authorizeCustomerAccess(authentication, customerId);
        LOGGER.info("Received KYC document upload request: customerId={}, documentType={}", customerId, documentType);
        KycDocumentResponse response = customerService.uploadKycDocument(customerId, documentType, file);
        LOGGER.info(
                "KYC document upload request completed: customerId={}, documentId={}",
                customerId,
                response.id()
        );
        return ApiResponseDto.success("documents upload sucessfully", response);
    }

    @PostMapping("/{customerId}/kyc/verify")
    @Operation(summary = "Verify a customer's KYC documents")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer KYC verified"),
            @ApiResponse(
                    responseCode = "400",
                    description = "KYC documents must be uploaded before verification",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ApiResponseDto<CustomerResponse> verifyKyc(
            @Parameter(description = "Positive customer ID", required = true)
            @PathVariable @Positive Long customerId,
            Principal principal
    ) {
        LOGGER.info("Received KYC verification request: customerId={}", customerId);
        CustomerResponse response = customerService.verifyKyc(customerId, principal.getName());
        LOGGER.info(
                "KYC verification request completed: customerId={}, kycStatus={}",
                customerId,
                response.kycStatus()
        );
        return ApiResponseDto.success(
                "Customer KYC verified successfully.",
                response
        );
    }

    @GetMapping("/{customerId}/kyc/status")
    @Operation(summary = "Get KYC status and latest review decision")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ApiResponseDto<KycStatusResponse> getKycStatus(@PathVariable @Positive Long customerId) {
        return ApiResponseDto.success(
                "KYC status retrieved successfully.",
                customerService.getKycStatus(customerId)
        );
    }

    @GetMapping("/{customerId}/kyc/documents")
    @Operation(summary = "List KYC document versions and metadata")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ApiResponseDto<List<KycDocumentSummaryResponse>> getKycDocuments(
            @PathVariable @Positive Long customerId
    ) {
        return ApiResponseDto.success(
                "KYC documents retrieved successfully.",
                customerService.getKycDocuments(customerId)
        );
    }

    @GetMapping("/{customerId}/kyc/documents/{documentId}/content")
    @Operation(summary = "Download an uploaded KYC document")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'KYC_REVIEWER')")
    public ResponseEntity<byte[]> getKycDocumentContent(
            @PathVariable @Positive Long customerId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        authorizeCustomerAccess(authentication, customerId);
        KycDocumentFileContent file = customerService.getKycDocumentContent(customerId, documentId);
        MediaType mediaType = MediaType.parseMediaType(file.mediaType());
        String extension = MediaType.APPLICATION_PDF.equals(mediaType) ? ".pdf" : ".png";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.content().length)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("kyc-document-" + documentId + extension)
                                .build()
                                .toString()
                )
                .body(file.content());
    }

    @DeleteMapping("/{customerId}/kyc/documents/{documentId}")
    @Operation(summary = "Delete a KYC document before verification")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ApiResponseDto<String> deleteKycDocument(
            @PathVariable @Positive Long customerId,
            @PathVariable UUID documentId
    ) {
        customerService.deleteKycDocument(customerId, documentId);
        return ApiResponseDto.success("KYC document deleted successfully.", "Deleted");
    }

    @PostMapping("/{customerId}/kyc/reject")
    @Operation(summary = "Reject a KYC submission with a reason")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ApiResponseDto<KycStatusResponse> rejectKyc(
            @PathVariable @Positive Long customerId,
            @Valid @RequestBody KycRejectionRequest request,
            Principal principal
    ) {
        return ApiResponseDto.success(
                "KYC submission rejected.",
                customerService.rejectKyc(customerId, request.reason(), principal.getName())
        );
    }

    @PostMapping(
            value = "/{customerId}/kyc/documents/resubmit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Append a new KYC document version after rejection")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ApiResponseDto<KycDocumentResponse> resubmitKyc(
            @PathVariable @Positive Long customerId,
            @RequestParam KycDocumentType documentType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        authorizeCustomerAccess(authentication, customerId);
        return ApiResponseDto.success(
                "KYC document resubmitted successfully.",
                customerService.resubmitKyc(customerId, documentType, file)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by UUID")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'KYC_REVIEWER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponseDto<CustomerResponse> getCustomerById(
            @Parameter(description = "Customer UUID", required = true)
            @PathVariable UUID id,
            Authentication authentication
    ) {
        authorizeCustomerAccess(authentication, id);
        return ApiResponseDto.success("Customer retrieved successfully.", customerService.getCustomerById(id));
    }

    @GetMapping("/by-customer-id/{customerId}")
    @Operation(summary = "Get a customer by public customer ID")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'KYC_REVIEWER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Customer ID must be positive",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponseDto<CustomerResponse> getCustomerByCustomerId(
            @Parameter(description = "Positive customer ID", required = true)
            @PathVariable Long customerId,
            Authentication authentication
    ) {
        authorizeCustomerAccess(authentication, customerId);
        return ApiResponseDto.success(
                "Customer retrieved successfully.",
                customerService.getCustomerByCustomerId(customerId)
        );
    }

    @GetMapping("/by-email")
    @Operation(summary = "Get a customer by email address")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Email is invalid",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponseDto<CustomerResponse> getCustomerByEmail(
            @Parameter(
                    description = "Customer email address",
                    required = true,
                    in = ParameterIn.QUERY,
                    schema = @Schema(format = "email")
            )
            @RequestParam @NotBlank @Email String email
    ) {
        return ApiResponseDto.success("Customer retrieved successfully.", customerService.getCustomerByEmail(email));
    }

    @GetMapping("/by-mobile-number")
    @Operation(summary = "Get a customer by mobile number")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Mobile number is invalid",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponseDto<CustomerResponse> getCustomerByMobileNumber(
            @Parameter(
                    description = "10-digit mobile number beginning with 6 through 9",
                    required = true,
                    in = ParameterIn.QUERY,
                    schema = @Schema(pattern = "^[6-9]\\d{9}$", example = "9876543210")
            )
            @RequestParam @Pattern(regexp = "^[6-9]\\d{9}$") String mobileNumber
    ) {
        return ApiResponseDto.success(
                "Customer retrieved successfully.",
                customerService.getCustomerByMobileNumber(mobileNumber)
        );
    }

    @GetMapping
    @Operation(summary = "List customers")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    @ApiResponse(responseCode = "200", description = "Customer page returned")
    public ApiResponseDto<Page<CustomerResponse>> getAllCustomers(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponseDto.success(
                "Customers retrieved successfully.",
                customerService.getAllCustomers(pageable)
        );
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a customer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'KYC_REVIEWER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email or mobile number is already registered",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponseDto<CustomerResponse> updateCustomer(
            @Parameter(description = "Customer UUID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody CustomerUpdateRequest request,
            Authentication authentication
    ) {
        authorizeCustomerAccess(authentication, id);
        return ApiResponseDto.success(
                "Customer updated successfully.",
                customerService.updateCustomer(id, request)
        );
    }

    private void authorizeCustomerAccess(Authentication authentication, Long customerId) {
        if (!authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_KYC_REVIEWER"))) {
            customerIdentityService.assertOwnsCustomerId(customerId, authenticatedJwt(authentication));
        }
    }

    private void authorizeCustomerAccess(Authentication authentication, UUID customerId) {
        if (!authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_KYC_REVIEWER"))) {
            customerIdentityService.assertOwnsCustomerUuid(customerId, authenticatedJwt(authentication));
        }
    }

    private Jwt authenticatedJwt(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "Authenticated identity is not a JWT.");
    }
}
