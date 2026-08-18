package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreatePinRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "PIN must be exactly 6 digits") String pin
) {
}
