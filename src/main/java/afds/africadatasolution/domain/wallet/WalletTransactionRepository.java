package afds.africadatasolution.domain.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByReference(String reference);

    Optional<WalletTransaction> findByReferenceAndWalletId(String reference, UUID walletId);

    long countByWalletId(UUID walletId);

    @Query(value = """
            select * from wallet_transactions where wallet_id = :walletId
            order by created_at desc limit :limit offset :offset
            """, nativeQuery = true)
    List<WalletTransaction> findPage(@Param("walletId") UUID walletId, @Param("limit") int limit, @Param("offset") int offset);

    @Query("""
            select coalesce(sum(t.amount), 0) from WalletTransaction t
            where t.walletId = :walletId and t.type = :type and t.status = 'COMPLETED'
            """)
    BigDecimal sumByWalletIdAndType(@Param("walletId") UUID walletId, @Param("type") TransactionType type);
}
