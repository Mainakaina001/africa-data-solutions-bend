package afds.africadatasolution.modules.auth.dto.response;

import afds.africadatasolution.domain.user.User;

import java.util.UUID;

public record UserSummary(UUID id, String email, String phone, String firstName, String lastName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getPhone(), user.getFirstName(), user.getLastName());
    }
}
