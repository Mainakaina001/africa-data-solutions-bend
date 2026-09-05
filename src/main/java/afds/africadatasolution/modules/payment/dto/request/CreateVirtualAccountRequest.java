package afds.africadatasolution.modules.payment.dto.request;

import jakarta.validation.constraints.Pattern;

public record CreateVirtualAccountRequest(
        @Pattern(regexp = "^(PALMPAY|OPAY)$",
                message = "Bank must be one of: PALMPAY, OPAY") String bank
) {
}
