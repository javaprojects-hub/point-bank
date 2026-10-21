package com.pointbank.bankplatform.dto;

import com.pointbank.bankplatform.constant.AddressType;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpdateRequest(
        AddressType addressType,

        @Pattern(regexp = ".*\\S.*", message = "address line 1 cannot be blank")
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @Pattern(regexp = ".*\\S.*", message = "city cannot be blank")
        @Size(max = 100)
        String city,

        @Pattern(regexp = ".*\\S.*", message = "state or province cannot be blank")
        @Size(max = 100)
        String stateOrProvince,

        @Pattern(regexp = ".*\\S.*", message = "postal code cannot be blank")
        @Size(max = 20)
        String postalCode,

        @Pattern(regexp = "^[A-Z]{2}$", message = "country code must be a two-letter uppercase ISO code")
        String countryCode,

        Boolean primary
) {
}
