package afds.africadatasolution.modules.external.smeplug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SmePlugDataResponse(boolean status, Data data, String message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String reference, String msg) {
    }
}
