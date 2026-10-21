package com.pointbank.bankplatform.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;

public record CustomerResponse(
        UUID id,
        Long customerId,
        String firstName,
        String middleName,
        String lastName,
        String gender,
        LocalDate dateOfBirth,
        String mobileNumber,
        String email,
        List<AddressResponse> addresses,
        CustomerStatus customerStatus,
        KycStatus kycStatus
) {
}
