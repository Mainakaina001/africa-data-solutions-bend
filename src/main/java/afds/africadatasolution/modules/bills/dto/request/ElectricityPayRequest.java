package afds.africadatasolution.modules.bills.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ElectricityPayRequest(
        @NotBlank String meterNumber,
        @NotBlank String serviceID,
        @NotBlank String variationCode,
        @NotNull @DecimalMin(value = "100", message = "Amount must be at least ₦100") BigDecimal amount,
        @Pattern(regexp = "^0[789][01]\\d{8}$") String phone,
        @Pattern(regexp = "^\\d{4,6}$") String pin
) {
}
