package agsfjope.backend.application.dtos.requests.notification;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkNotificationIdsRequest {

    @NotEmpty(message = "Danh sách notificationId không được để trống")
    private List<UUID> notificationIds;
}