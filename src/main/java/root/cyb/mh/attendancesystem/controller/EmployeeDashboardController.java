package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletResponse;

import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;
import root.cyb.mh.attendancesystem.service.PdfExportService;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class EmployeeDashboardController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PdfExportService pdfExportService;

    @GetMapping("/employee/dashboard")
    public String dashboard(Model model, Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(new Employee());

        // 1. Basic Employee Info
        model.addAttribute("employee", employee);

        // 2. Shift / Schedule Info (Global for now, as per ReportService logic)
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(null);
        model.addAttribute("workSchedule", schedule);

        // 3. Recent Logs (Last 10)
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeId(employeeId);
        List<AttendanceLog> recentLogs = allLogs.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());
        model.addAttribute("recentLogs", recentLogs);

        // 4. Monthly Stats Calculation
        calculateMonthlyStats(model, allLogs, schedule, employeeId);

        // 5. Annual Quota Stats
        calculateAnnualStats(model, schedule, employeeId, employee);

        return "employee-dashboard";
    }

    private void calculateAnnualStats(Model model, WorkSchedule schedule, String employeeId, Employee employee) {
        int defaultQuota = schedule != null && schedule.getDefaultAnnualLeaveQuota() != null
                ? schedule.getDefaultAnnualLeaveQuota()
                : 12;
        int quota = employee.getEffectiveQuota(defaultQuota);

        // Calculate Total Approved Leaves for Current Year
        int currentYear = LocalDate.now().getYear();
        List<root.cyb.mh.attendancesystem.model.LeaveRequest> leaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        long yearlyLeavesCount = leaves.stream()
                .filter(l -> l.getEmployee().getId().equals(employeeId))
                .filter(l -> l.getStartDate().getYear() == currentYear || l.getEndDate().getYear() == currentYear)
                .mapToLong(l -> {
                    // Calculate intersection with current year
                    LocalDate start = l.getStartDate().getYear() < currentYear ? LocalDate.of(currentYear, 1, 1)
                            : l.getStartDate();
                    LocalDate end = l.getEndDate().getYear() > currentYear ? LocalDate.of(currentYear, 12, 31)
                            : l.getEndDate();

                    if (start.isAfter(end))
                        return 0;
                    return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
                })
                .sum();

        int totalTaken = (int) yearlyLeavesCount;
        int paidTaken = Math.min(totalTaken, quota);
        int unpaidTaken = Math.max(0, totalTaken - quota);

        model.addAttribute("annualQuota", quota);
        model.addAttribute("yearlyLeavesTaken", totalTaken);
        model.addAttribute("paidLeavesTaken", paidTaken);
        model.addAttribute("unpaidLeavesTaken", unpaidTaken);
    }

    private void calculateMonthlyStats(Model model, List<AttendanceLog> allLogs, WorkSchedule schedule,
            String employeeId) {
        LocalDate now = LocalDate.now();
        YearMonth currentYearMonth = YearMonth.from(now);

        // Filter logs for current month
        List<AttendanceLog> monthLogs = allLogs.stream()
                .filter(log -> YearMonth.from(log.getTimestamp()).equals(currentYearMonth))
                .collect(Collectors.toList());

        // Days Present (Count distinct days)
        long daysPresent = monthLogs.stream()
                .map(log -> log.getTimestamp().toLocalDate())
                .distinct()
                .count();
        model.addAttribute("daysPresent", daysPresent);

        // Late & Early Stats
        int lateCount = 0;
        int earlyCount = 0;

        if (schedule != null) {
            for (LocalDate date = currentYearMonth.atDay(1); !date.isAfter(now); date = date.plusDays(1)) {
                LocalDate finalDate = date;
                List<AttendanceLog> dayLogs = monthLogs.stream()
                        .filter(log -> log.getTimestamp().toLocalDate().equals(finalDate))
                        .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                        .collect(Collectors.toList());

                if (!dayLogs.isEmpty()) {
                    // Check In (First Log)
                    LocalTime checkIn = dayLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime startTime = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes()); // Fixed
                                                                                                                   // getter
                    if (checkIn.isAfter(startTime)) {
                        lateCount++;
                    }

                    // Check Out (Last Log) - Logic for Early Departure
                    // Only calculate if we have at least 2 logs (In and Out) or distinct timestamps
                    if (dayLogs.size() > 1) {
                        LocalTime checkOut = dayLogs.get(dayLogs.size() - 1).getTimestamp().toLocalTime();

                        // Calculate allowed earliest departure time (EndTime - Tolerance)
                        LocalTime allowedExitTime = schedule.getEndTime()
                                .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                        // If check out is BEFORE Allowed Exit Time
                        if (checkOut.isBefore(allowedExitTime)) {
                            earlyCount++;
                        }
                    }
                }
            }
        }
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("earlyCount", earlyCount);

        // Leaves Calculation (Monthly)
        int leaveCount = 0;
        if (employeeId != null) {
            final String empId = employeeId;
            List<root.cyb.mh.attendancesystem.model.LeaveRequest> leaves = leaveRequestRepository
                    .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

            long leavesThisMonth = leaves.stream()
                    .filter(l -> l.getEmployee().getId().equals(empId))
                    .filter(l -> {
                        // Check overlap with current month
                        LocalDate start = l.getStartDate();
                        LocalDate end = l.getEndDate();
                        // simple check: if any day of leave is in current month
                        return !start.isAfter(now) && !end.isBefore(currentYearMonth.atDay(1));
                    })
                    .mapToLong(l -> {
                        // Count days in this month
                        LocalDate s = l.getStartDate().isBefore(currentYearMonth.atDay(1)) ? currentYearMonth.atDay(1)
                                .plusDays(0) // hack to copy
                                : l.getStartDate();
                        // Count up to 'now' or end of month? Let's say end of month logic for stats
                        LocalDate monthEnd = currentYearMonth.atEndOfMonth();
                        LocalDate realEnd = l.getEndDate().isAfter(monthEnd) ? monthEnd : l.getEndDate();

                        if (s.isAfter(realEnd))
                            return 0;

                        return java.time.temporal.ChronoUnit.DAYS.between(s, realEnd) + 1;
                    })
                    .sum();
            leaveCount = (int) leavesThisMonth;
        }
        model.addAttribute("leaveCount", leaveCount);
    }

    @GetMapping("/employee/attendance/history")
    public String attendanceHistory(
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            Model model, Principal principal) {

        String employeeId = principal.getName();
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        // Determine Dates based on Period
        if ("3M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(2).withDayOfMonth(1); // Current + prev 2
        } else if ("6M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(5).withDayOfMonth(1);
        } else if ("1Y".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(11).withDayOfMonth(1);
        } else {
            // Specific Month (Default)
            if (year == null)
                year = now.getYear();
            if (month == null)
                month = now.getMonthValue();

            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            period = "MONTH"; // Default marker
        }

        // Fetch Range Data
        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeData = reportService
                .getEmployeeRangeReport(employeeId, startDate, endDate);

        model.addAttribute("data", rangeData);
        model.addAttribute("selectedPeriod", period);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("activeLink", "history");

        return "employee-attendance-history";
    }

    @GetMapping("/employee/report/monthly/download")
    public void downloadAttendanceReport(
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            HttpServletResponse response, Principal principal) throws Exception {

        String employeeId = principal.getName();
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        // Determine Dates (Same logic as View)
        if ("3M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(2).withDayOfMonth(1);
        } else if ("6M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(5).withDayOfMonth(1);
        } else if ("1Y".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(11).withDayOfMonth(1);
        } else {
            // Specific Month (Default)
            if (year == null)
                year = now.getYear();
            if (month == null)
                month = now.getMonthValue();

            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            period = "MONTH_" + month + "_" + year;
        }

        // 1. Get Range Data
        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto reportData = reportService
                .getEmployeeRangeReport(employeeId, startDate, endDate);

        if (reportData == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Employee data not found");
            return;
        }

        // 2. Generate PDF using Range Export
        // Note: Even for a single month, we now use the Range logic (List of 1 report)
        // via exportEmployeeRangeReport
        // or we could keep the old one. But Range logic is cleaner as it handles the
        // list loop.
        byte[] pdfBytes = pdfExportService.exportEmployeeRangeReport(reportData);

        // 3. Write Response
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=Attendance_Report_" + period + "_" + System.currentTimeMillis() + ".pdf");
        response.getOutputStream().write(pdfBytes);
    }
}
