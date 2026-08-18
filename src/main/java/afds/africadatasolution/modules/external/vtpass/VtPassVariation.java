package afds.africadatasolution.modules.external.vtpass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VtPassVariation(String variation_code, String name, String variation_amount, String fixedPrice) {
}
