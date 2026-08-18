package afds.africadatasolution.modules.external.billstack;

public record BillstackVirtualAccountRequest(
        String email,
        String reference,
        String firstName,
        String lastName,
        String phone,
        String bank
) {
}
