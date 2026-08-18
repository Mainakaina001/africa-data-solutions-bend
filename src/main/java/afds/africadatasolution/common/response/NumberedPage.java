package afds.africadatasolution.common.response;

import java.util.List;

/** page/limit pagination envelope — mirrors the {orders,pagination:{page,limit,total,pages}} shape used by
 * backend/src/controllers/airtime.controller.ts and backend/src/controllers/bills.controller.ts. */
public record NumberedPage<T>(List<T> items, Pagination pagination) {

    public static <T> NumberedPage<T> of(List<T> items, int page, int limit, long total) {
        int pages = (int) Math.ceil((double) total / limit);
        return new NumberedPage<>(items, new Pagination(page, limit, total, pages));
    }

    public record Pagination(int page, int limit, long total, int pages) {
    }
}
