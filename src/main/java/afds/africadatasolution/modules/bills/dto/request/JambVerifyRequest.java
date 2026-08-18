package afds.africadatasolution.modules.bills.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JambVerifyRequest(@NotBlank String profileId, @NotBlank String variationCode) {
}
