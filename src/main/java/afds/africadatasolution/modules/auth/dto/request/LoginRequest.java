package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @Size(min = 6, max = 10) String twoFactorCode
) {
}
