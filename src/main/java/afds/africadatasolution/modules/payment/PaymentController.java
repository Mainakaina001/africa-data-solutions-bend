package afds.africadatasolution.modules.payment;

import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.modules.payment.dto.request.CreateVirtualAccountRequest;
import afds.africadatasolution.modules.payment.dto.response.FundingInitiationResponse;
import afds.africadatasolution.modules.payment.dto.response.FundingVerificationResponse;
import afds.africadatasolution.modules.payment.dto.request.InitiateFundingRequest;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountCreatedResponse;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountListItem;
import afds.africadatasolution.modules.payment.service.PaymentService;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import afds.africadatasolution.modules.wallet.dto.response.WalletTransactionView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors backend/src/routes/payment.routes.ts + backend/src/controllers/payment.controller.ts (minus the webhook). */
@RestController
@RequestMapping("/api/v1/wallet")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/virtual-account/create")
    public ApiResponse<VirtualAccountCreatedResponse> createVirtualAccount(@CurrentUser AuthUser user,
                                                                   @Valid @RequestBody CreateVirtualAccountRequest request) {
        var result = paymentService.createVirtualAccount(user.id(), request.bank());
        String message = result.alreadyExisted() ? "Virtual account already exists" : "Virtual account created successfully";
        return ApiResponse.success(message, VirtualAccountCreatedResponse.from(result.account()));
    }

    @GetMapping("/virtual-accounts")
    public ApiResponse<List<VirtualAccountListItem>> getVirtualAccounts(@CurrentUser AuthUser user) {
        return ApiResponse.success("Virtual accounts retrieved successfully", paymentService.getVirtualAccounts(user.id()));
    }

    @PostMapping("/fund/initiate")
    public ApiResponse<FundingInitiationResponse> initiateFunding(@CurrentUser AuthUser user,
                                                                    @Valid @RequestBody InitiateFundingRequest request) {
        return ApiResponse.success("Payment initialization successful", paymentService.initiateFunding(user.id(), request.amount()));
    }

    @GetMapping("/fund/verify/{reference}")
    public ApiResponse<FundingVerificationResponse> verifyFunding(@CurrentUser AuthUser user, @PathVariable String reference) {
        WalletTransactionView txn = paymentService.verifyFunding(user.id(), reference);
        return ApiResponse.success("Payment verified successfully", new FundingVerificationResponse("success", txn));
    }
}
