package afds.africadatasolution.modules.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterTokenRequest(@NotBlank @Size(min = 32, max = 4096) String fcmToken) {
}
