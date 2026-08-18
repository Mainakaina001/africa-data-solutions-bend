package afds.africadatasolution.modules.payment.dto.request;

import jakarta.validation.constraints.Pattern;

public record CreateVirtualAccountRequest(
        @Pattern(regexp = "^(PALMPAY|9PSB|SAFEHAVEN|PROVIDUS|BANKLY)$",
                message = "Bank must be one of: PALMPAY, 9PSB, SAFEHAVEN, PROVIDUS, BANKLY") String bank
) {
}
