package afds.africadatasolution.modules.external.smeplug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SME Plug is inconsistent about which field carries the failure reason —
 * {@code /data/purchase} and {@code /airtime/purchase} use {@code errors}
 * (e.g. {@code {"status":false,"errors":"Invalid beneficiary"}}), so both are
 * mapped; see {@link #failureMessage(String)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmePlugDataResponse(boolean status, Data data, String message, String errors) {

    public String failureMessage(String fallback) {
        if (message != null) return message;
        if (errors != null) return errors;
        return fallback;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String reference, String msg) {
    }
}
