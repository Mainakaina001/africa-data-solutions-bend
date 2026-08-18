package afds.africadatasolution.modules.external.smeplug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SmePlugNetworksResponse(boolean status, Map<String, String> networks, String message) {
}
