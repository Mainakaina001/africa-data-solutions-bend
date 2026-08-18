package afds.africadatasolution.modules.notification;

import java.util.Map;

public record NotificationPayload(String title, String body, Map<String, String> data, String imageUrl) {

    public NotificationPayload(String title, String body, Map<String, String> data) {
        this(title, body, data, null);
    }
}
