package com.pointbank.bankplatform.dto;

import com.pointbank.bankplatform.constant.AddressType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotNull(message = "address type cannot be null")
        AddressType addressType,

        @NotBlank(message = "address line 1 cannot be blank")
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank(message = "city cannot be blank")
        @Size(max = 100)
        String city,

        @NotBlank(message = "state or province cannot be blank")
        @Size(max = 100)
        String stateOrProvince,

        @NotBlank(message = "postal code cannot be blank")
        @Size(max = 20)
        String postalCode,

        @NotBlank(message = "country code cannot be blank")
        @Pattern(regexp = "^[A-Z]{2}$", message = "country code must be a two-letter uppercase ISO code")
        String countryCode,

        boolean primary
) {
}
