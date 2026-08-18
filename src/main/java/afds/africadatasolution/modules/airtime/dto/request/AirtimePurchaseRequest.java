package afds.africadatasolution.modules.airtime.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record AirtimePurchaseRequest(
        @Pattern(regexp = "^(mtn|airtel|etisalat|glo)$", message = "Network must be one of: mtn, airtel, etisalat, glo") String network,
        @Pattern(regexp = "^0[789][01]\\d{8}$", message = "Valid Nigerian phone number is required") String phone,
        @NotNull @DecimalMin(value = "50", message = "Minimum airtime amount is ₦50")
        @DecimalMax(value = "50000", message = "Maximum airtime amount is ₦50000") BigDecimal amount,
        @Pattern(regexp = "^\\d{4,6}$", message = "Transaction PIN required") String pin
) {
}
