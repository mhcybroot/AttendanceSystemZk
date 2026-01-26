package root.cyb.mh.attendancesystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import root.cyb.mh.attendancesystem.model.Asset;
import root.cyb.mh.attendancesystem.model.Employee;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetTimelineDTO implements Comparable<AssetTimelineDTO> {

    private LocalDateTime timestamp;
    private Type type;

    // For Assignments
    private Employee employee;
    private LocalDateTime returnDate;
    private String returnCondition;
    private String duration;

    // For Status Changes
    private Asset.Status oldStatus;
    private Asset.Status newStatus;

    // Shared
    private String description; // Condition or Reason

    @Override
    public int compareTo(AssetTimelineDTO o) {
        // Sort descending (newest first)
        return o.getTimestamp().compareTo(this.getTimestamp());
    }

    public enum Type {
        ASSIGNMENT,
        STATUS_CHANGE
    }
}
