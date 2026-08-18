package afds.africadatasolution.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Enable2FARequest(@NotBlank @Size(min = 6, max = 10) String code) {
}
