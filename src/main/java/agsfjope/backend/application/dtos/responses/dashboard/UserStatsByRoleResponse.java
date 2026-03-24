package agsfjope.backend.application.dtos.responses.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for the user statistics by role panel on the Admin Dashboard.
 * <p>
 * Contains the total user count and a per-role breakdown used to render
 * the donut chart ("Thống kê người dùng theo Role").
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsByRoleResponse {

    /** Total number of non-deleted users across all roles. */
    private long totalUsers;

    /** Per-role user counts used to render the donut chart segments. */
    private List<RoleCount> roles;

    /**
     * A single segment in the user-by-role donut chart.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleCount {

        /** Role name as stored in the database (e.g. "STUDENT"). */
        private String roleName;

        /** Human-readable role label for display (e.g. "Students"). */
        private String displayName;

        /** Number of non-deleted users with this role. */
        private long count;
    }
}
