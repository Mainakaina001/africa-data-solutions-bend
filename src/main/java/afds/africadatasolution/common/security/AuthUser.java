package afds.africadatasolution.common.security;

import afds.africadatasolution.domain.user.UserRole;

import java.util.UUID;

/** Authenticated-principal snapshot attached to the request — mirrors backend/src/types/index.ts#AuthRequest.user. */
public record AuthUser(UUID id, String email, String phone, UserRole role) {
}
