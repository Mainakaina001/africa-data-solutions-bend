package afds.africadatasolution.modules.external.vtpass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VtPassVariationsResponse(String response_description, Content content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(String ServiceName, String serviceID, String convinience_fee, List<VtPassVariation> variations) {
    }
}
