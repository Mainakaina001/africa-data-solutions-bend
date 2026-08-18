package afds.africadatasolution.common.response;

import java.util.List;

/** limit/offset pagination envelope — mirrors the {data, pagination:{total,limit,offset,hasMore}} shape used by
 * backend/src/services/wallet.service.ts and backend/src/services/data.service.ts. */
public record OffsetPage<T>(List<T> items, Pagination pagination) {

    public static <T> OffsetPage<T> of(List<T> items, long total, int limit, int offset) {
        boolean hasMore = offset + limit < total;
        return new OffsetPage<>(items, new Pagination(total, limit, offset, hasMore));
    }

    public record Pagination(long total, int limit, int offset, boolean hasMore) {
    }
}
