package afds.africadatasolution.modules.payment;

import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.modules.payment.dto.response.WebhookProcessResponse;
import afds.africadatasolution.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Billstack (legacy) and PaymentPoint (current) webhook entry points.
 * Deliberately reads the RAW request bytes itself (no {@code @RequestBody})
 * so each HMAC signature is verified against the exact bytes the provider
 * signed — re-serializing a parsed JSON object before verifying would let
 * subtle re-encoding differences bypass the signature check. Mirrors
 * backend/src/app.ts's raw-body webhook router.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final PaymentService paymentService;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @SecurityRequirements
    @PostMapping("/billstack")
    public ApiResponse<WebhookProcessResponse> handleBillstackWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Billstack-Signature", required = false) String signatureBillstack,
            @RequestHeader(value = "X-Wiaxy-Signature", required = false) String signatureWiaxy) throws IOException {

        byte[] raw = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (raw.length > MAX_BODY_BYTES) {
            throw new ValidationException("Webhook payload too large");
        }

        var result = paymentService.handleWebhook(raw, signatureBillstack, signatureWiaxy);
        String message = result.duplicate() ? "Already received" : "Webhook processed successfully";
        return ApiResponse.success(message, new WebhookProcessResponse(result.processed(), result.externalId()));
    }

    @SecurityRequirements
    @PostMapping("/paymentpoint")
    public ApiResponse<WebhookProcessResponse> handlePaymentPointWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Paymentpoint-Signature", required = false) String signature) throws IOException {

        byte[] raw = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (raw.length > MAX_BODY_BYTES) {
            throw new ValidationException("Webhook payload too large");
        }

        var result = paymentService.handlePaymentPointWebhook(raw, signature);
        String message = result.duplicate() ? "Already received" : "Webhook processed successfully";
        return ApiResponse.success(message, new WebhookProcessResponse(result.processed(), result.externalId()));
    }
}
