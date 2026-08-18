package afds.africadatasolution.modules.notification;

import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.modules.notification.dto.request.RegisterTokenRequest;
import afds.africadatasolution.modules.notification.dto.request.TestNotificationRequest;
import afds.africadatasolution.modules.notification.dto.response.NotificationSentResponse;
import afds.africadatasolution.modules.notification.dto.response.TokenRegisteredResponse;
import afds.africadatasolution.modules.notification.dto.response.TokenRemovedResponse;
import afds.africadatasolution.modules.notification.service.NotificationService;
import afds.africadatasolution.common.security.AuthUser;
import afds.africadatasolution.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * FCM token registration. The admin-only test endpoint sends only to the
 * calling admin's own token — never to arbitrary users (phishing prevention).
 * Mirrors backend/src/controllers/notification.controller.ts.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/register-token")
    public ApiResponse<TokenRegisteredResponse> registerToken(@CurrentUser AuthUser user, @Valid @RequestBody RegisterTokenRequest request) {
        notificationService.saveToken(user.id(), request.fcmToken());
        return ApiResponse.success("Notification token registered successfully", new TokenRegisteredResponse(true));
    }

    @DeleteMapping("/token")
    public ApiResponse<TokenRemovedResponse> removeToken(@CurrentUser AuthUser user) {
        notificationService.clearToken(user.id());
        return ApiResponse.success("Notification token removed successfully", new TokenRemovedResponse(true));
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationSentResponse> testNotification(@CurrentUser AuthUser user, @Valid @RequestBody TestNotificationRequest request) {
        boolean sent = notificationService.sendToUser(user.id(), new NotificationPayload(
                request.title(), request.body(), Map.of("type", "test")));
        return ApiResponse.success(sent ? "Test notification sent" : "Failed to send notification", new NotificationSentResponse(sent));
    }
}
