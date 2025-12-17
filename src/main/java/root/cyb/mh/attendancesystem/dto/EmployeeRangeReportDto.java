package root.cyb.mh.attendancesystem.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class EmployeeRangeReportDto {
    private String employeeName;
    private String employeeId;
    private String departmentName;

    private LocalDate startDate;
    private LocalDate endDate;

    // Aggregate Stats
    private int totalPresent;
    private int totalAbsent;
    private int totalLates;
    private int totalEarlyLeaves;
    private int totalLeaves;
    private int totalPaidLeaves;
    private int totalUnpaidLeaves;

    // Monthly Breakdown
    private List<EmployeeMonthlyDetailDto> monthlyReports = new ArrayList<>();
}
