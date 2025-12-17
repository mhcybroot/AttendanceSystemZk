package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

        @Autowired
        private EmployeeRepository employeeRepository;

        @Autowired
        private DepartmentRepository departmentRepository;

        @Autowired
        private AttendanceLogRepository attendanceLogRepository;

        @Autowired
        private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

        @Autowired
        private ReportService reportService;

        @GetMapping({ "/", "/dashboard" })
        public String dashboard(Model model) {
                LocalDate today = LocalDate.now();

                // Count Stats
                long totalEmployees = employeeRepository.count();
                long totalDepartments = departmentRepository.count();

                // Today's Attendance Stats
                List<DailyAttendanceDto> dailyReport = reportService
                                .getDailyReport(today, null, org.springframework.data.domain.PageRequest.of(0, 5000))
                                .getContent();
                long presentCount = dailyReport.stream()
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                || d.getStatus().contains("EARLY"))
                                .count();
                // Recent Activity (Last 5 logs)
                List<AttendanceLog> recentLogs = attendanceLogRepository.findByTimestampBetween(
                                today.atStartOfDay(), today.atTime(LocalTime.MAX))
                                .stream()
                                .sorted(Comparator.comparing(AttendanceLog::getTimestamp).reversed())
                                .limit(5)
                                .collect(Collectors.toList());

                // Enrich logs with names
                recentLogs.forEach(log -> {
                        employeeRepository.findById(log.getEmployeeId()).ifPresent(emp -> {
                                // This block was just checking/loading, effectively doing nothing but ensuring
                                // lazy load if any
                                // Since we use DTO or direct access in view, this might be redundant but safe
                                // to keep for now
                        });
                });

                // Leaves Today
                long leaveCount = leaveRequestRepository
                                .countByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(
                                                today, today,
                                                root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

                // Status Breakdown
                long absentCount = totalEmployees - presentCount - leaveCount;
                if (absentCount < 0)
                        absentCount = 0;

                model.addAttribute("totalEmployees", totalEmployees);
                model.addAttribute("totalDepartments", totalDepartments);
                model.addAttribute("presentCount", presentCount);
                model.addAttribute("absentCount", absentCount);
                model.addAttribute("leaveCount", leaveCount);
                model.addAttribute("recentLogs", recentLogs);

                // Chart 1: Today's Status (Donut)
                // Order: Present, On Leave, Absent
                java.util.List<Long> statusChartData = java.util.Arrays.asList(presentCount, leaveCount,
                                absentCount);
                model.addAttribute("statusChartData", statusChartData);

                model.addAttribute("employeeMap", employeeRepository.findAll().stream()
                                .collect(Collectors.toMap(root.cyb.mh.attendancesystem.model.Employee::getId,
                                                root.cyb.mh.attendancesystem.model.Employee::getName)));

                return "dashboard";
        }
}
