package afds.africadatasolution.modules.auth.dto.response;

import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRole;

import java.util.UUID;

/**
 * User projection returned on login/profile — deliberately excludes internal
 * security counters (tokenVersion, failedLoginAttempts, lockedUntil) that the
 * original Node response leaked to the client.
 */
public record AuthenticatedUserView(UUID id, String email, String phone, String firstName, String lastName,
                                     UserRole role, boolean isActive) {

    public static AuthenticatedUserView from(User user) {
        return new AuthenticatedUserView(user.getId(), user.getEmail(), user.getPhone(), user.getFirstName(),
                user.getLastName(), user.getRole(), user.isActive());
    }
}
