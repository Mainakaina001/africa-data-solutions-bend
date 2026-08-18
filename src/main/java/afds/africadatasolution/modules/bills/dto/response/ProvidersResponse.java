package afds.africadatasolution.modules.bills.dto.response;

import afds.africadatasolution.modules.external.vtpass.ProviderInfo;

import java.util.List;

public record ProvidersResponse(List<ProviderInfo> providers) {
}
