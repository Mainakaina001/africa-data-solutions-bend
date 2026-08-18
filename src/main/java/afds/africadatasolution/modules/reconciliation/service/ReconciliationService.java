package afds.africadatasolution.modules.reconciliation.service;

/**
 * Reconciliation worker — sweeps orders stuck in PROCESSING, requeries the
 * upstream provider where possible, and either completes or refunds them.
 * Also periodically verifies wallet balances against their transaction
 * ledgers (drift is logged/audited but never auto-corrected — an operator
 * decision). Runs in-process on a schedule rather than as the separate
 * {@code worker:reconcile} Node process. Mirrors backend/src/workers/reconciliation.ts.
 */
public interface ReconciliationService {

    void reconcileAll();

    void verifyWalletBalances();
}
