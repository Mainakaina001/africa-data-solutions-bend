package afds.africadatasolution.modules.bills.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmartcardVerifyRequest(
        @NotBlank String smartcardNumber,
        @Pattern(regexp = "^(dstv|gotv|startimes)$") String serviceID
) {
}
