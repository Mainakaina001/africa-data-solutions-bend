package afds.africadatasolution.modules.auth.dto.response;

import java.util.List;

public record TwoFactorEnableResponse(List<String> backupCodes) {
}
