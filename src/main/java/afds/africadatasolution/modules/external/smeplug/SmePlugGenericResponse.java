package afds.africadatasolution.modules.external.smeplug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Loosely-typed envelope for endpoints whose payload shape we don't need to fully model (balance, query). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmePlugGenericResponse(boolean status, Map<String, Object> data, String message) {
}
