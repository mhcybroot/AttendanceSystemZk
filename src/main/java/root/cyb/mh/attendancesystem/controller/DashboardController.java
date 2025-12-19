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

                // Fetch All Employees
                List<root.cyb.mh.attendancesystem.model.Employee> allEmployees = employeeRepository.findAll();

                // Identify Guests
                List<String> guestIds = allEmployees.stream()
                                .filter(root.cyb.mh.attendancesystem.model.Employee::isGuest)
                                .map(root.cyb.mh.attendancesystem.model.Employee::getId)
                                .collect(Collectors.toList());

                // Count Stats (Excluding Guests)
                long totalEmployees = allEmployees.size() - guestIds.size();
                long totalDepartments = departmentRepository.count();

                // Today's Attendance Stats (Present/Late/Early)
                List<DailyAttendanceDto> dailyReport = reportService
                                .getDailyReport(today, null, null,
                                                org.springframework.data.domain.PageRequest.of(0, 5000))
                                .getContent();

                // Filter out guests from daily report
                long presentCount = dailyReport.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                || d.getStatus().contains("EARLY"))
                                .count();

                // Recent Activity (Last 5 logs) - Keep showing all logs, or filter?
                // User said "Today's Status will skip if employee is guest".
                // Logs are "Recent Activity", likely okay to show anyone who punched.
                // But to be consistent with "excluding guests", maybe we should filter too?
                // The requirement was specific to "Today's Status" (the counters).
                // I will keep Recent Activity as is (raw logs) unless requested.
                List<AttendanceLog> recentLogs = attendanceLogRepository.findByTimestampBetween(
                                today.atStartOfDay(), today.atTime(LocalTime.MAX))
                                .stream()
                                .sorted(Comparator.comparing(AttendanceLog::getTimestamp).reversed())
                                .limit(5)
                                .collect(Collectors.toList());

                recentLogs.forEach(log -> {
                        employeeRepository.findById(log.getEmployeeId()).ifPresent(emp -> {
                        });
                });

                // Leaves Today (Excluding Guests)
                List<root.cyb.mh.attendancesystem.model.LeaveRequest> todayLeaves = leaveRequestRepository
                                .findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(
                                                today, today,
                                                root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

                long leaveCount = todayLeaves.stream()
                                .filter(l -> !guestIds.contains(l.getEmployee().getId()))
                                .count();

                // Late Count (Excluding Guests)
                long lateCount = dailyReport.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .filter(d -> d.getStatus().contains("LATE"))
                                .count();

                // Status Breakdown
                long absentCount = totalEmployees - presentCount - leaveCount;
                if (absentCount < 0)
                        absentCount = 0;

                model.addAttribute("totalEmployees", totalEmployees);
                model.addAttribute("totalDepartments", totalDepartments);
                model.addAttribute("presentCount", presentCount);
                model.addAttribute("absentCount", absentCount);
                model.addAttribute("leaveCount", leaveCount);
                model.addAttribute("lateCount", lateCount);
                model.addAttribute("recentLogs", recentLogs);

                // Chart 1: Today's Status (Donut)
                java.util.List<Long> statusChartData = java.util.Arrays.asList(presentCount, leaveCount,
                                absentCount);
                model.addAttribute("statusChartData", statusChartData);

                model.addAttribute("employeeMap", allEmployees.stream()
                                .collect(Collectors.toMap(root.cyb.mh.attendancesystem.model.Employee::getId,
                                                root.cyb.mh.attendancesystem.model.Employee::getName)));

                return "dashboard";
        }
}
