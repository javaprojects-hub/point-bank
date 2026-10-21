package com.pointbank.bankplatform.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "first name cannot be blank")
        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String middleName,

        @Pattern(regexp = ".*\\S.*", message = "last name cannot be blank")
        @Size(max = 100)
        String lastName,

        @Pattern(regexp = ".*\\S.*", message = "gender cannot be blank")
        String gender,

        @Past(message = "date of birth must be in the past")
        LocalDate dateOfBirth,

        @Pattern(regexp = "^[6-9]\\d{9}$", message = "mobile number must be 10 digits and start with 6 through 9")
        String mobileNumber,

        @Pattern(regexp = ".*\\S.*", message = "email cannot be blank")
        @Email
        String email
) {
}
