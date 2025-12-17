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
        private ReportService reportService;

        @GetMapping({ "/", "/dashboard" })
        public String dashboard(Model model) {
                LocalDate today = LocalDate.now();

                // Count Stats
                long totalEmployees = employeeRepository.count();
                long totalDepartments = departmentRepository.count();

                // Today's Attendance Stats
                List<DailyAttendanceDto> dailyReport = reportService.getDailyReport(today, null);
                long presentCount = dailyReport.stream()
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                || d.getStatus().contains("EARLY"))
                                .count();
                long latersCount = dailyReport.stream()
                                .filter(d -> d.getStatus().contains("LATE"))
                                .count();

                long absentCount = totalEmployees - presentCount; // Rough estimate, precise absent count depends on
                                                                  // dailyReport
                                                                  // logic

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
                                // Temporary way to carry name, better to use DTO but staying simple for now
                                // or just look it up in template using a map if needed,
                                // but let's assume log display uses ID or we pass a map.
                        });
                });

                model.addAttribute("totalEmployees", totalEmployees);
                model.addAttribute("totalDepartments", totalDepartments);
                model.addAttribute("presentCount", presentCount);
                model.addAttribute("absentCount", absentCount);
                model.addAttribute("latersCount", latersCount);
                model.addAttribute("recentLogs", recentLogs);

                // Chart Data (Last 7 Days)
                java.util.List<String> chartLabels = new java.util.ArrayList<>();
                java.util.List<Long> chartData = new java.util.ArrayList<>();

                LocalDate startOfChart = today.minusDays(6);
                List<AttendanceLog> weeklyLogs = attendanceLogRepository.findByTimestampBetween(
                                startOfChart.atStartOfDay(), today.atTime(LocalTime.MAX));

                for (int i = 6; i >= 0; i--) {
                        LocalDate date = today.minusDays(i);
                        chartLabels.add(date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")));

                        // Count distinct employees present on this date
                        long presentCountForDate = weeklyLogs.stream()
                                        .filter(l -> l.getTimestamp().toLocalDate().equals(date))
                                        .map(AttendanceLog::getEmployeeId)
                                        .distinct()
                                        .count();

                        chartData.add(presentCountForDate);
                }

                model.addAttribute("chartLabels", chartLabels);
                model.addAttribute("chartData", chartData);
                model.addAttribute("employeeMap", employeeRepository.findAll().stream()
                                .collect(Collectors.toMap(root.cyb.mh.attendancesystem.model.Employee::getId,
                                                root.cyb.mh.attendancesystem.model.Employee::getName)));

                return "dashboard";
        }
}
