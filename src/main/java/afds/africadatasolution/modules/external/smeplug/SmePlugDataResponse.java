package afds.africadatasolution.modules.external.smeplug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SME Plug is inconsistent about which field carries the failure reason —
 * {@code /data/purchase} and {@code /airtime/purchase} use {@code errors}
 * (e.g. {@code {"status":false,"errors":"Invalid beneficiary"}}), and for some
 * failures (e.g. no SIM registered for a Share plan, or the dispensing SIM
 * itself is out of balance) the reason instead only appears in
 * {@code data.msg} (e.g. {@code {"status":false,"data":{"msg":"You do not
 * have an active sim to dispense plan."}}}) — confirmed directly against
 * their live API — so all three are checked; see {@link #failureMessage(String)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmePlugDataResponse(boolean status, Data data, String message, String errors) {

    public String failureMessage(String fallback) {
        if (message != null) return message;
        if (errors != null) return errors;
        if (data != null && data.msg() != null) return data.msg();
        return fallback;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String reference, String msg) {
    }
}
