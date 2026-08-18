package afds.africadatasolution.modules.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InitiateFundingRequest(@NotNull @DecimalMin(value = "100", message = "Minimum funding amount is ₦100") BigDecimal amount) {
}
