package afds.africadatasolution.modules.external.billstack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BillstackVerifyResponse(boolean status, String message, Map<String, Object> data) {
}
