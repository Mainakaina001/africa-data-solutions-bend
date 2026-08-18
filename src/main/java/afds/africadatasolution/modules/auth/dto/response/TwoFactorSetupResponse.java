package afds.africadatasolution.modules.auth.dto.response;

public record TwoFactorSetupResponse(String otpauthUrl, String qrCodeDataUrl) {
}
