package com.pointbank.bankplatform.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "first name cannot be blank")
        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String middleName,

        @NotBlank(message = "last name cannot be blank")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "gender cannot be blank")
        String gender,

        @NotNull(message = "date of birth cannot be null")
        @Past(message = "date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "mobile number cannot be blank")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "mobile number must be 10 digits and start with 6 through 9")
        String mobileNumber,

        @NotBlank(message = "email cannot be blank")
        @Email
        String email,

        @NotBlank(message = "password cannot be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

        @Valid
        List<@Valid AddressRequest> addresses
) {
}
