package afds.africadatasolution.modules.bills;

import afds.africadatasolution.modules.auth.service.PinVerificationService;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.common.response.NumberedPage;
import afds.africadatasolution.domain.order.BillCategory;
import afds.africadatasolution.modules.bills.dto.request.*;
import afds.africadatasolution.modules.bills.dto.response.*;
import afds.africadatasolution.modules.bills.service.BillsService;
import afds.africadatasolution.modules.external.vtpass.VtPassClient;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** Mirrors backend/src/routes/bills.routes.ts + backend/src/controllers/bills.controller.ts. */
@RestController
@RequestMapping("/api/v1/bills")
public class BillsController {

    private static final Set<String> ELECTRICITY_IDS = Set.of(
            "ikeja-electric", "eko-electric", "kano-electric", "phed", "enugu-electric",
            "abuja-electric", "ibadan-electric", "jos-electric", "kaduna-electric", "benin-electric");

    private final BillsService billsService;
    private final PinVerificationService pinVerificationService;

    public BillsController(BillsService billsService, PinVerificationService pinVerificationService) {
        this.billsService = billsService;
        this.pinVerificationService = pinVerificationService;
    }

    @GetMapping("/electricity/providers")
    public ApiResponse<ProvidersResponse> getElectricityProviders() {
        return ApiResponse.success("Electricity providers retrieved successfully", new ProvidersResponse(VtPassClient.ELECTRICITY_PROVIDERS));
    }

    @GetMapping("/tv/providers")
    public ApiResponse<ProvidersResponse> getTvProviders() {
        return ApiResponse.success("TV providers retrieved successfully", new ProvidersResponse(VtPassClient.TV_PROVIDERS));
    }

    @GetMapping("/education/providers")
    public ApiResponse<ProvidersResponse> getEducationProviders() {
        return ApiResponse.success("Education providers retrieved successfully", new ProvidersResponse(VtPassClient.EDUCATION_PROVIDERS));
    }

    @GetMapping("/variations/{serviceID}")
    public ApiResponse<ServiceVariationsResponse> getVariations(@PathVariable String serviceID) {
        var data = billsService.getVariations(serviceID);
        return ApiResponse.success("Service variations retrieved successfully", new ServiceVariationsResponse(
                data.content().ServiceName(), data.content().serviceID(), data.content().convinience_fee(), data.content().variations()));
    }

    @PostMapping("/electricity/verify")
    public ApiResponse<MeterVerificationResponse> verifyMeter(@Valid @RequestBody MeterVerifyRequest request) {
        if (!ELECTRICITY_IDS.contains(request.serviceID())) throw new ValidationException("Invalid electricity serviceID");
        var result = billsService.verifyMeter(request.meterNumber(), request.serviceID(), request.type());
        return ApiResponse.success("Meter verified successfully", new MeterVerificationResponse(
                String.valueOf(result.contentString("Customer_Name")),
                String.valueOf(result.contentString("Address")),
                result.contentString("Meter_Number") != null ? result.contentString("Meter_Number") : request.meterNumber(),
                String.valueOf(result.contentString("Status"))));
    }

    @PostMapping("/tv/verify")
    public ApiResponse<SmartcardVerificationResponse> verifySmartcard(@Valid @RequestBody SmartcardVerifyRequest request) {
        var result = billsService.verifySmartcard(request.smartcardNumber(), request.serviceID());
        return ApiResponse.success("Smartcard verified successfully", new SmartcardVerificationResponse(
                String.valueOf(result.contentString("Customer_Name")),
                String.valueOf(result.contentString("Status")),
                String.valueOf(result.contentString("Due_Date")),
                request.smartcardNumber()));
    }

    @PostMapping("/education/verify")
    public ApiResponse<JambVerificationResponse> verifyJambProfile(@Valid @RequestBody JambVerifyRequest request) {
        var result = billsService.verifyJambProfile(request.profileId(), request.variationCode());
        return ApiResponse.success("JAMB profile verified successfully",
                new JambVerificationResponse(String.valueOf(result.contentString("Customer_Name")), request.profileId()));
    }

    @PostMapping("/electricity/pay")
    public ApiResponse<BillPaymentResultResponse> payElectricity(@CurrentUser AuthUser user, @Valid @RequestBody ElectricityPayRequest request) {
        if (!ELECTRICITY_IDS.contains(request.serviceID())) throw new ValidationException("Invalid electricity serviceID");
        pinVerificationService.verify(user.id(), request.pin());
        var result = billsService.payElectricity(user.id(), request.meterNumber(), request.serviceID(), request.variationCode(),
                request.amount(), request.phone());
        return respond(result);
    }

    @PostMapping("/tv/pay")
    public ApiResponse<BillPaymentResultResponse> payTv(@CurrentUser AuthUser user, @Valid @RequestBody TvPayRequest request) {
        pinVerificationService.verify(user.id(), request.pin());
        var result = billsService.payTv(user.id(), request.smartcardNumber(), request.serviceID(), request.variationCode(),
                request.amount(), request.phone(), request.subscriptionType());
        return respond(result);
    }

    @PostMapping("/education/pay")
    public ApiResponse<BillPaymentResultResponse> payEducation(@CurrentUser AuthUser user, @Valid @RequestBody EducationPayRequest request) {
        pinVerificationService.verify(user.id(), request.pin());
        var result = billsService.payEducation(user.id(), request.serviceID(), request.variationCode(), request.amount(),
                request.phone(), request.quantityOrDefault());
        return respond(result);
    }

    private ApiResponse<BillPaymentResultResponse> respond(BillsService.BillPaymentResult result) {
        String message = result.status().name().equals("COMPLETED") ? "payment successful" : "payment submitted";
        return ApiResponse.success(message, BillPaymentResultResponse.from(result));
    }

    @GetMapping("/history")
    public ApiResponse<NumberedPage<BillPaymentView>> getHistory(
            @CurrentUser AuthUser user,
            @RequestParam(required = false) BillCategory category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        var result = billsService.getHistory(user.id(), category, page, limit);
        return ApiResponse.success("Bill payment history retrieved successfully",
                new NumberedPage<>(result.items().stream().map(BillPaymentView::from).toList(), result.pagination()));
    }

    @GetMapping("/{reference}")
    public ApiResponse<BillPaymentView> getByReference(@CurrentUser AuthUser user, @PathVariable String reference) {
        return ApiResponse.success("Bill payment retrieved successfully", BillPaymentView.from(billsService.getByReference(user.id(), reference)));
    }
}
