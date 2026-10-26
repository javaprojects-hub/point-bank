package com.pointbank.bankplatform.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.pointbank.bankplatform.dto.CustomerRequest;
import com.pointbank.bankplatform.dto.CustomerResponse;
import com.pointbank.bankplatform.dto.CustomerUpdateRequest;
import com.pointbank.bankplatform.entity.Customer;

@Mapper(
        componentModel = "spring",
        uses = AddressMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "mobileNumberVerified", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "identityIssuer", ignore = true)
    @Mapping(target = "identitySubject", ignore = true)
    @Mapping(target = "customerStatus", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "kycVerifiedAt", ignore = true)
    @Mapping(target = "kycReviewedAt", ignore = true)
    @Mapping(target = "kycReviewedBy", ignore = true)
    @Mapping(target = "kycRejectionReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toEntity(CustomerRequest request);

    CustomerResponse toResponse(Customer customer);

    @BeanMapping(
            ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "middleName", source = "middleName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "dateOfBirth", source = "dateOfBirth")
    @Mapping(target = "mobileNumber", source = "mobileNumber")
    @Mapping(target = "email", source = "email")
    void updateEntity(CustomerUpdateRequest request, @MappingTarget Customer customer);

    @AfterMapping
    default void linkAddresses(@MappingTarget Customer customer) {
        customer.getAddresses().forEach(address -> address.setCustomer(customer));
    }
}
