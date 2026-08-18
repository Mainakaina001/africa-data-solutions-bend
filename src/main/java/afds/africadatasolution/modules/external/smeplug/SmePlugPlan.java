package afds.africadatasolution.modules.external.smeplug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SmePlugPlan(int id, String name, String dispense_method, BigDecimal telco_price, BigDecimal price) {
}
