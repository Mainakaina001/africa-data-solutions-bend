package afds.africadatasolution.modules.data.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record DataPurchaseRequest(
        @NotNull(message = "Valid data plan ID is required") UUID dataPlanId,
        @Pattern(regexp = "^(0|\\+234)[789][01]\\d{8}$", message = "Valid Nigerian phone number is required") String phone,
        @Pattern(regexp = "^\\d{4,6}$", message = "Transaction PIN required") String pin
) {
}
