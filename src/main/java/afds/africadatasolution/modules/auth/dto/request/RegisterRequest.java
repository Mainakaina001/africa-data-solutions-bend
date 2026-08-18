package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Pattern(regexp = "^0[789][01]\\d{8}$", message = "Valid Nigerian phone number is required (e.g., 08012345678)")
        String phone,
        @NotBlank @Size(min = 2, max = 50) String firstName,
        @NotBlank @Size(min = 2, max = 50) String lastName,
        @NotBlank @Size(min = 12, max = 128) String password
) {
}
