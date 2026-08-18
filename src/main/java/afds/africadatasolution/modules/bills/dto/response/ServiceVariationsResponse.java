package afds.africadatasolution.modules.bills.dto.response;

import afds.africadatasolution.modules.external.vtpass.VtPassVariation;

import java.util.List;

public record ServiceVariationsResponse(String serviceName, String serviceID, String convenienceFee, List<VtPassVariation> variations) {
}
