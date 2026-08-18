package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Email @NotBlank String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Reset token must be 6 digits") String resetToken,
        @NotBlank @Size(min = 12, max = 128) String newPassword
) {
}
