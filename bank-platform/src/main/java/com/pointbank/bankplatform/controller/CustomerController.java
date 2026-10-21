package com.pointbank.bankplatform.controller;

import java.net.URI;
import java.util.UUID;

import com.pointbank.bankplatform.constant.KycDocumentType;
import com.pointbank.bankplatform.dto.ApiResponseDto;
import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.dto.KycDocumentResponse;
import com.pointbank.bankplatform.exception.ApiErrorResponse;
import com.pointbank.bankplatform.service.CustomerService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
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

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
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

    @PostMapping(value = "/{id}/kyc/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    public ApiResponseDto<KycDocumentResponse> uploadKycDocument(
            @PathVariable UUID id,
            @RequestParam KycDocumentType documentType,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponseDto.success(
                "KYC document uploaded and submitted for review.",
                customerService.uploadKycDocument(id, documentType, file)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by UUID")
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
            @PathVariable UUID id
    ) {
        return ApiResponseDto.success("Customer retrieved successfully.", customerService.getCustomerById(id));
    }

    @GetMapping("/by-customer-id/{customerId}")
    @Operation(summary = "Get a customer by public customer ID")
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
            @PathVariable Long customerId
    ) {
        return ApiResponseDto.success(
                "Customer retrieved successfully.",
                customerService.getCustomerByCustomerId(customerId)
        );
    }

    @GetMapping("/by-email")
    @Operation(summary = "Get a customer by email address")
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
            @Valid @RequestBody CustomerUpdateRequest request
    ) {
        return ApiResponseDto.success(
                "Customer updated successfully.",
                customerService.updateCustomer(id, request)
        );
    }
}
