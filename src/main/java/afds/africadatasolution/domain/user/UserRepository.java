package afds.africadatasolution.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmailOrPhone(String email, String phone);

    @Modifying
    @Query("update User u set u.fcmToken = null where u.fcmToken = :token")
    void clearFcmToken(@Param("token") String token);
}
