package root.cyb.mh.attendancesystem.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.Map;

@Data
public class WeeklyAttendanceDto {
    private String employeeName;
    private String employeeId;
    private String departmentName;

    // Map of Date -> Status (e.g., "PRESENT", "ABSENT", "WEEKEND")
    private Map<LocalDate, String> dailyStatus;

    // Summary
    private int presentCount;
    private int absentCount;
    private int lateCount;
    private int earlyLeaveCount;
    private int leaveCount;
}
