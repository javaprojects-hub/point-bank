package com.pointbank.bankplatform.dto;

import com.pointbank.bankplatform.constant.AddressType;

public record AddressResponse(
        AddressType addressType,
        String addressLine1,
        String addressLine2,
        String city,
        String stateOrProvince,
        String postalCode,
        String countryCode,
        boolean primary
) {
}
