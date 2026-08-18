package afds.africadatasolution.modules.auth;

import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.modules.auth.dto.request.*;
import afds.africadatasolution.modules.auth.dto.response.*;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import afds.africadatasolution.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Mirrors backend/src/routes/auth.routes.ts + backend/src/controllers/auth.controller.ts. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                                  HttpServletRequest httpRequest) {
        RegisterResponse response = authService.register(request, clientIp(httpRequest), userAgent(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Registration successful", response));
    }

    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                              HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, clientIp(httpRequest), userAgent(httpRequest));
        String message = response.twoFactorRequired() ? "2FA required" : "Login successful";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokens>> refresh(@Valid @RequestBody RefreshRequest request,
                                                             HttpServletRequest httpRequest) {
        AuthTokens tokens = authService.refresh(request, clientIp(httpRequest), userAgent(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Refreshed", tokens));
    }

    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request != null ? request : new LogoutRequest(null));
        return ResponseEntity.ok(ApiResponse.success("Logged out"));
    }

    @PostMapping("/logout-everywhere")
    public ResponseEntity<ApiResponse<Void>> logoutEverywhere(@CurrentUser AuthUser user, HttpServletRequest httpRequest) {
        authService.logoutEverywhere(user.id(), clientIp(httpRequest), userAgent(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Logged out everywhere"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@CurrentUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", authService.getProfile(user.id())));
    }

    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                              HttpServletRequest httpRequest) {
        authService.forgotPassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("If your email is registered, you will receive a password reset code"));
    }

    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                             HttpServletRequest httpRequest) {
        authService.resetPassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Password reset successful"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@CurrentUser AuthUser user,
                                                              @Valid @RequestBody ChangePasswordRequest request,
                                                              HttpServletRequest httpRequest) {
        authService.changePassword(user.id(), request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @PostMapping("/create-pin")
    public ResponseEntity<ApiResponse<Void>> createPin(@CurrentUser AuthUser user, @Valid @RequestBody CreatePinRequest request,
                                                         HttpServletRequest httpRequest) {
        authService.createTransactionPin(user.id(), request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transaction PIN created successfully"));
    }

    @PostMapping("/change-pin")
    public ResponseEntity<ApiResponse<Void>> changePin(@CurrentUser AuthUser user, @Valid @RequestBody ChangePinRequest request,
                                                         HttpServletRequest httpRequest) {
        authService.changeTransactionPin(user.id(), request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Transaction PIN changed successfully"));
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup2FA(@CurrentUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.success("2FA setup initiated", authService.setup2FA(user.id())));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enable2FA(@CurrentUser AuthUser user,
                                                                          @Valid @RequestBody Enable2FARequest request,
                                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("2FA enabled", authService.enable2FA(user.id(), request, clientIp(httpRequest))));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<Void>> disable2FA(@CurrentUser AuthUser user, @Valid @RequestBody Disable2FARequest request,
                                                          HttpServletRequest httpRequest) {
        authService.disable2FA(user.id(), request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("2FA disabled"));
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
