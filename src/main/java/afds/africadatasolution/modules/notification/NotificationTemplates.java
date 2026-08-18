package afds.africadatasolution.modules.notification;

import java.math.BigDecimal;
import java.util.Map;

/** Push-notification copy for each domain event. Mirrors NotificationService.templates in backend/src/services/notification.service.ts. */
public final class NotificationTemplates {

    private NotificationTemplates() {
    }

    public static NotificationPayload walletFunded(BigDecimal amount) {
        return new NotificationPayload(
                "💰 Wallet Funded",
                "Your wallet has been credited with ₦" + amount,
                Map.of("type", "wallet_funded", "amount", amount.toString()));
    }

    public static NotificationPayload dataPurchaseSuccess(String dataAmount, String phone) {
        return new NotificationPayload(
                "✅ Data Purchase Successful",
                dataAmount + " has been sent to " + phone,
                Map.of("type", "data_purchase_success", "dataAmount", dataAmount, "phone", phone));
    }

    public static NotificationPayload dataPurchaseFailed(String dataAmount, String reason) {
        return new NotificationPayload(
                "❌ Data Purchase Failed",
                "Failed to send " + dataAmount + ". Your wallet has been refunded.",
                Map.of("type", "data_purchase_failed", "dataAmount", dataAmount, "reason", reason));
    }

    public static NotificationPayload lowBalance(BigDecimal balance) {
        return new NotificationPayload(
                "⚠️ Low Balance",
                "Your wallet balance is low: ₦" + balance + ". Top up now!",
                Map.of("type", "low_balance", "balance", balance.toString()));
    }
}
