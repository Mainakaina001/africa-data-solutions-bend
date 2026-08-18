package afds.africadatasolution.modules.airtime;

import afds.africadatasolution.modules.airtime.dto.response.AirtimeOrderView;
import afds.africadatasolution.modules.airtime.dto.response.AirtimePurchaseResponse;
import afds.africadatasolution.modules.airtime.dto.request.AirtimePurchaseRequest;
import afds.africadatasolution.modules.airtime.service.AirtimeService;
import afds.africadatasolution.modules.auth.service.PinVerificationService;
import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.common.response.NumberedPage;
import afds.africadatasolution.modules.external.vtpass.ProviderInfo;
import afds.africadatasolution.modules.external.vtpass.VtPassClient;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors backend/src/routes/airtime.routes.ts + backend/src/controllers/airtime.controller.ts. */
@RestController
@RequestMapping("/api/v1/airtime")
public class AirtimeController {

    private final AirtimeService airtimeService;
    private final PinVerificationService pinVerificationService;

    public AirtimeController(AirtimeService airtimeService, PinVerificationService pinVerificationService) {
        this.airtimeService = airtimeService;
        this.pinVerificationService = pinVerificationService;
    }

    @GetMapping("/networks")
    public ApiResponse<List<ProviderInfo>> getNetworks() {
        return ApiResponse.success("Networks retrieved successfully", VtPassClient.AIRTIME_NETWORKS);
    }

    @PostMapping("/purchase")
    public ApiResponse<AirtimePurchaseResponse> purchase(@CurrentUser AuthUser user, @Valid @RequestBody AirtimePurchaseRequest request) {
        pinVerificationService.verify(user.id(), request.pin());
        var result = airtimeService.purchase(user.id(), request);
        return ApiResponse.success("Airtime purchase processed", new AirtimePurchaseResponse(
                result.reference(), result.vtpassRequestId(), request.phone(), request.amount(), request.network(), result.status()));
    }

    @GetMapping("/history")
    public ApiResponse<NumberedPage<AirtimeOrderView>> getHistory(@CurrentUser AuthUser user,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "20") int limit) {
        var result = airtimeService.getHistory(user.id(), page, limit);
        return ApiResponse.success("Airtime history retrieved successfully",
                new NumberedPage<>(result.items().stream().map(AirtimeOrderView::summary).toList(), result.pagination()));
    }

    @GetMapping("/{reference}")
    public ApiResponse<AirtimeOrderView> getByReference(@CurrentUser AuthUser user, @PathVariable String reference) {
        return ApiResponse.success("Airtime order retrieved successfully",
                AirtimeOrderView.detail(airtimeService.getByReference(user.id(), reference)));
    }
}
