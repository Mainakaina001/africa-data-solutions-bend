package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank @Pattern(regexp = "^\\d{4,6}$") String currentPin,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String newPin
) {
}
