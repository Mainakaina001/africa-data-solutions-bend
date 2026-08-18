package afds.africadatasolution.modules.bills.dto.response;

public record SmartcardVerificationResponse(String customerName, String status, String dueDate, String smartcardNumber) {
}
