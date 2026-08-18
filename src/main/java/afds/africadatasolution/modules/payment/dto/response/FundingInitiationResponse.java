package afds.africadatasolution.modules.payment.dto.response;

import java.math.BigDecimal;

public record FundingInitiationResponse(String reference, String authorizationUrl, String accessCode, BigDecimal amount) {
}
