package afds.africadatasolution.modules.data;

import afds.africadatasolution.modules.auth.service.PinVerificationService;
import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.common.response.OffsetPage;
import afds.africadatasolution.modules.data.dto.response.DataOrderView;
import afds.africadatasolution.modules.data.dto.response.DataPlanView;
import afds.africadatasolution.modules.data.dto.response.DataPurchaseResponse;
import afds.africadatasolution.modules.data.dto.request.DataPurchaseRequest;
import afds.africadatasolution.modules.data.service.DataService;
import afds.africadatasolution.modules.external.smeplug.NetworkPlans;
import afds.africadatasolution.modules.external.smeplug.SmePlugClient;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Data plans + purchase + history. Mirrors backend/src/routes/data.routes.ts +
 * backend/src/controllers/data.controller.ts. /plans/live and /plans/raw are
 * privileged — they expose upstream telco pricing / margin.
 */
@RestController
@RequestMapping("/api/v1/data")
public class DataController {

    private final DataService dataService;
    private final SmePlugClient smePlugClient;
    private final PinVerificationService pinVerificationService;

    public DataController(DataService dataService, SmePlugClient smePlugClient, PinVerificationService pinVerificationService) {
        this.dataService = dataService;
        this.smePlugClient = smePlugClient;
        this.pinVerificationService = pinVerificationService;
    }

    @GetMapping("/plans/live")
    @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
    public ApiResponse<List<NetworkPlans>> getLivePlans() {
        return ApiResponse.success("Live data plans retrieved successfully", smePlugClient.getFormattedPlans());
    }

    @GetMapping("/plans/raw")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> getRawPlans() {
        return ApiResponse.success("Raw SME Plug plans retrieved successfully", smePlugClient.getDataPlans().data());
    }

    @GetMapping("/plans")
    public ApiResponse<List<DataPlanView>> getPlans(@RequestParam(required = false) String network) {
        var plans = dataService.getDataPlans(network).stream().map(DataPlanView::from).toList();
        return ApiResponse.success("Data plans retrieved successfully", plans);
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<DataPlanView> getPlanById(@PathVariable UUID id) {
        return ApiResponse.success("Data plan retrieved successfully", DataPlanView.from(dataService.getPlanById(id)));
    }

    @PostMapping("/buy")
    public ApiResponse<DataPurchaseResponse> buyData(@CurrentUser AuthUser user, @Valid @RequestBody DataPurchaseRequest request) {
        pinVerificationService.verify(user.id(), request.pin());
        var result = dataService.purchaseData(user.id(), request);
        return ApiResponse.success(result.message(), result.result());
    }

    @GetMapping("/orders")
    public ApiResponse<OffsetPage<DataOrderView>> getOrders(@CurrentUser AuthUser user,
                                                             @RequestParam(defaultValue = "50") int limit,
                                                             @RequestParam(defaultValue = "0") int offset) {
        var page = dataService.getUserOrders(user.id(), limit, offset);
        return ApiResponse.success("Orders retrieved successfully",
                new OffsetPage<>(page.items().stream().map(DataOrderView::from).toList(), page.pagination()));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<DataOrderView> getOrderById(@CurrentUser AuthUser user, @PathVariable UUID id) {
        return ApiResponse.success("Order retrieved successfully", DataOrderView.from(dataService.getOrderById(id, user.id())));
    }
}
