package afds.africadatasolution.modules.external.vtpass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** {@code content} is intentionally loosely typed — VTPass returns a different field set per service (meter,
 * smartcard, JAMB profile...); mirrors the index-signature type in backend/src/types/index.ts#VTPassVerifyResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VtPassVerifyResponse(String code, Map<String, Object> content) {

    public String contentString(String key) {
        Object v = content == null ? null : content.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
