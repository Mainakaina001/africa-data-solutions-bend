package afds.africadatasolution.modules.auth.service;


import afds.africadatasolution.modules.auth.dto.request.*;
import afds.africadatasolution.modules.auth.dto.response.*;

import java.util.UUID;

/**
 * Authentication, session, and account-security flows.
 * Mirrors backend/src/controllers/auth.controller.ts.
 */

public interface AuthService {
    RegisterResponse register(RegisterRequest request, String ip, String userAgent);
    LoginResponse login(LoginRequest request, String ip, String userAgent);
    AuthTokens refresh(RefreshRequest request, String ip, String userAgent);
    void logout(LogoutRequest request);
    void logoutEverywhere(UUID userId, String ip, String userAgent);
    ProfileResponse getProfile(UUID userId);
    void forgotPassword(ForgotPasswordRequest request, String ip);
    void resetPassword(ResetPasswordRequest request, String ip);
    void changePassword(UUID userId, ChangePasswordRequest request, String ip);
    void createTransactionPin(UUID userId, CreatePinRequest request, String ip);
    void changeTransactionPin(UUID userId, ChangePinRequest request, String ip);
    TwoFactorEnableResponse enable2FA(UUID userId, Enable2FARequest request, String ip);
    void disable2FA(UUID userId, Disable2FARequest request, String ip);
    TwoFactorSetupResponse setup2FA(UUID userId);
}
