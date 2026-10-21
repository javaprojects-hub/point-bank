package com.pointbank.bankplatform.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.pointbank.bankplatform.dto.AddressRequest;
import com.pointbank.bankplatform.dto.AddressResponse;
import com.pointbank.bankplatform.dto.AddressUpdateRequest;
import com.pointbank.bankplatform.entity.Address;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AddressMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Address toEntity(AddressRequest request);

    AddressResponse toResponse(Address address);

    @BeanMapping(
            ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "addressType", source = "addressType")
    @Mapping(target = "addressLine1", source = "addressLine1")
    @Mapping(target = "addressLine2", source = "addressLine2")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "stateOrProvince", source = "stateOrProvince")
    @Mapping(target = "postalCode", source = "postalCode")
    @Mapping(target = "countryCode", source = "countryCode")
    @Mapping(target = "primary", source = "primary")
    void updateEntity(AddressUpdateRequest request, @MappingTarget Address address);
}
