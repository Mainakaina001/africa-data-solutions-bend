package afds.africadatasolution.modules.bills.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MeterVerifyRequest(
        @NotBlank String meterNumber,
        @NotBlank String serviceID,
        @Pattern(regexp = "^(prepaid|postpaid)$") String type
) {
}
