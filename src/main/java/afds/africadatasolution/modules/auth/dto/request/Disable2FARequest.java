package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record Disable2FARequest(@NotBlank String password) {
}
