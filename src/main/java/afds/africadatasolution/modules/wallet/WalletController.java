package afds.africadatasolution.modules.wallet;

import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.common.response.OffsetPage;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import afds.africadatasolution.modules.wallet.dto.response.WalletDetailsView;
import afds.africadatasolution.modules.wallet.dto.response.WalletTransactionView;
import afds.africadatasolution.modules.wallet.service.WalletService;
import org.springframework.web.bind.annotation.*;

/** IDOR-safe: every lookup is scoped to the requesting user's own wallet. Mirrors backend/src/controllers/wallet.controller.ts. */
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ApiResponse<WalletDetailsView> getWallet(@CurrentUser AuthUser user) {
        return ApiResponse.success("Wallet retrieved successfully",
                WalletDetailsView.from(walletService.getWalletByUserId(user.id())));
    }

    @GetMapping("/balance")
    public ApiResponse<WalletService.BalanceView> getBalance(@CurrentUser AuthUser user) {
        return ApiResponse.success("Balance retrieved successfully", walletService.getBalance(user.id()));
    }

    @GetMapping("/transactions")
    public ApiResponse<OffsetPage<WalletTransactionView>> getTransactions(
            @CurrentUser AuthUser user,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var page = walletService.getTransactions(user.id(), limit, offset);
        var view = new OffsetPage<>(page.items().stream().map(WalletTransactionView::from).toList(), page.pagination());
        return ApiResponse.success("Transactions retrieved successfully", view);
    }

    @GetMapping("/transactions/{reference}")
    public ApiResponse<WalletTransactionView> getTransactionByReference(@CurrentUser AuthUser user, @PathVariable String reference) {
        var txn = walletService.getTransactionByReferenceForUser(reference, user.id());
        return ApiResponse.success("Transaction retrieved successfully", WalletTransactionView.from(txn));
    }
}
