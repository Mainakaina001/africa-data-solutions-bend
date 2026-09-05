package afds.africadatasolution.modules.payment.service;

import afds.africadatasolution.modules.payment.dto.response.FundingInitiationResponse;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountListItem;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountSummary;
import afds.africadatasolution.modules.wallet.dto.response.WalletTransactionView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Virtual accounts, wallet funding, and the provider webhooks. New virtual
 * accounts are issued via PaymentPoint; the Billstack webhook stays wired up
 * only to keep crediting funds sent to already-issued Billstack accounts.
 * Mirrors backend/src/controllers/payment.controller.ts.
 *
 * Webhook security: signature is verified against the RAW request bytes
 * (never a re-serialized JSON object — that would let a byte-for-byte
 * signature bypass through re-encoding quirks). {@code WebhookEvent} dedupes
 * by (provider, externalId) so redelivery/replay is a no-op.
 */
public interface PaymentService {

    CreateVirtualAccountResult createVirtualAccount(UUID userId, String bank);

    List<VirtualAccountListItem> getVirtualAccounts(UUID userId);

    FundingInitiationResponse initiateFunding(UUID userId, BigDecimal amount);

    WalletTransactionView verifyFunding(UUID userId, String reference);

    WebhookResult handleWebhook(byte[] rawBody, String signatureBillstack, String signatureWiaxy);

    WebhookResult handlePaymentPointWebhook(byte[] rawBody, String signature);

    record CreateVirtualAccountResult(VirtualAccountSummary account, boolean alreadyExisted) {
    }

    record WebhookResult(boolean processed, boolean duplicate, String externalId) {
    }
}
