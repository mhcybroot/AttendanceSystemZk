package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto;
import root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;
import root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.WorkSchedule;
import root.cyb.mh.attendancesystem.repository.AdvanceSalaryRepository;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.PublicHolidayRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

        @Autowired
        private EmployeeRepository employeeRepository;

        @Autowired
        private AttendanceLogRepository attendanceLogRepository;

        @Autowired
        private WorkScheduleRepository workScheduleRepository;

        @Autowired
        private ReportService reportService;

        @Autowired
        private BadgeService badgeService;

        @Autowired
        private AdvanceSalaryRepository advanceSalaryRepository;

        @Autowired
        private PublicHolidayRepository publicHolidayRepository;

        public Map<String, Object> getDashboardData(String employeeId) {
                return getDashboardData(employeeId, null, null);
        }

        public Map<String, Object> getDashboardData(String employeeId, Integer year, Integer month) {
                Map<String, Object> data = new HashMap<>();

                LocalDate now = LocalDate.now();
                LocalDate targetDate = now;

                if (year != null && month != null) {
                        targetDate = LocalDate.of(year, month, 1);
                }

                Employee employee = employeeRepository.findById(employeeId).orElse(new Employee());
                data.put("employee", employee);

                // 2. Shift / Schedule Info (Always distinct check for "Today" vs "Historical"?)
                // Schedule is usually "current" schedule. For history, it might be complex if
                // schedules change.
                // We will keep showing "Current Effective Schedule" for now as per common
                // simplicity.
                WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst()
                                .orElse(new WorkSchedule());
                WorkSchedule todaySchedule = reportService.resolveSchedule(employeeId, now, globalSchedule);
                data.put("workSchedule", todaySchedule);

                // 3. Recent Logs (Last 10) - Keep this as "Global Recent" for context
                List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeId(employeeId);
                List<AttendanceLog> recentLogs = allLogs.stream()
                                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                                .limit(10)
                                .collect(Collectors.toList());
                data.put("recentLogs", recentLogs);

                // 4. Monthly Stats (Target Month)
                EmployeeMonthlyDetailDto monthlyReport = reportService.getEmployeeMonthlyReport(
                                employeeId, targetDate.getYear(), targetDate.getMonthValue());

                if (monthlyReport != null) {
                        data.put("daysPresent", monthlyReport.getTotalPresent());
                        data.put("lateCount", monthlyReport.getTotalLates());
                        data.put("earlyCount", monthlyReport.getTotalEarlyLeaves());
                        data.put("leaveCount", monthlyReport.getTotalLeaves());

                        // Check if calculateBadges requires a list for the second argument
                        // Assuming badgeService.calculateBadges(monthlyReport, null) is valid as per
                        // original code
                        // Calculate Badges
                        List<String> badges = badgeService.calculateBadges(monthlyReport, null);
                        data.put("badges", badges);

                        // Expose full report for detailed views
                        data.put("monthlyReport", monthlyReport);
                } else {
                        data.put("daysPresent", 0);
                        data.put("lateCount", 0);
                        data.put("earlyCount", 0);
                        data.put("leaveCount", 0);
                        data.put("badges", new ArrayList<>());
                        data.put("monthlyReport", null);
                }

                // 5. Annual Quota Stats
                int defaultQuota = globalSchedule.getDefaultAnnualLeaveQuota() != null
                                ? globalSchedule.getDefaultAnnualLeaveQuota()
                                : 12;
                int quota = employee.getEffectiveQuota(defaultQuota);

                LocalDate startOfYear = LocalDate.of(now.getYear(), 1, 1);
                LocalDate endOfYear = LocalDate.of(now.getYear(), 12, 31);

                EmployeeRangeReportDto annualReport = reportService.getEmployeeRangeReport(
                                employeeId, startOfYear, endOfYear);

                int totalTaken = annualReport != null ? annualReport.getTotalLeaves() : 0;
                int paidTaken = annualReport != null ? annualReport.getTotalPaidLeaves() : 0;
                int unpaidTaken = annualReport != null ? annualReport.getTotalUnpaidLeaves() : 0;

                data.put("annualQuota", quota);
                data.put("yearlyLeavesTaken", totalTaken);
                data.put("paidLeavesTaken", paidTaken);
                data.put("unpaidLeavesTaken", unpaidTaken);

                // 6. Next Holiday Countdown
                java.util.Optional<root.cyb.mh.attendancesystem.model.PublicHoliday> nextHoliday = publicHolidayRepository
                                .findAll().stream()
                                .filter(h -> h.getDate().isAfter(now))
                                .sorted(java.util.Comparator
                                                .comparing(root.cyb.mh.attendancesystem.model.PublicHoliday::getDate))
                                .findFirst();

                if (nextHoliday.isPresent()) {
                        long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now, nextHoliday.get().getDate());
                        data.put("nextHoliday", nextHoliday.get());
                        data.put("daysUntilHoliday", daysUntil);
                } else {
                        data.put("nextHoliday", null);
                }

                // 7. Advance Salary Requests
                List<AdvanceSalaryRequest> myRequests = advanceSalaryRepository.findByEmployeeId(employeeId);
                myRequests.sort((a, b) -> b.getRequestDate().compareTo(a.getRequestDate()));
                data.put("advanceRequests", myRequests);

                // --- INSPIRATION METRICS (Global) ---
                addGlobalStats(data, now);

                return data;
        }

        private void addGlobalStats(Map<String, Object> data, LocalDate now) {
                List<Employee> allEmployees = employeeRepository.findAll();
                List<String> guestIds = allEmployees.stream().filter(Employee::isGuest).map(Employee::getId)
                                .collect(Collectors.toList());

                List<DailyAttendanceDto> dailyReport = reportService
                                .getDailyReport(now, null, null,
                                                org.springframework.data.domain.PageRequest.of(0, 5000))
                                .getContent();

                // 1. Early Birds
                List<DailyAttendanceDto> earlyBirds = dailyReport.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .filter(d -> d.getInTime() != null)
                                .sorted(Comparator.comparing(DailyAttendanceDto::getInTime))
                                .limit(5)
                                .collect(Collectors.toList());
                data.put("earlyBirds", earlyBirds);

                // 2. Department Champion
                Map<String, List<DailyAttendanceDto>> byDept = dailyReport.stream()
                                .filter(d -> d.getDepartmentName() != null
                                                && !d.getDepartmentName().equals("Unassigned"))
                                .collect(Collectors.groupingBy(DailyAttendanceDto::getDepartmentName));

                String championDept = "N/A";
                double maxPercent = -1.0;

                for (Map.Entry<String, List<DailyAttendanceDto>> entry : byDept.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("Guest"))
                                continue;
                        long deptTotal = entry.getValue().size();
                        if (deptTotal == 0)
                                continue;
                        long deptPresent = entry.getValue().stream()
                                        .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                        || d.getStatus().contains("EARLY"))
                                        .count();
                        double percent = (double) deptPresent / deptTotal;
                        if (percent > maxPercent) {
                                maxPercent = percent;
                                championDept = entry.getKey();
                        }
                }
                if (maxPercent <= 0) {
                        championDept = "No Data";
                        maxPercent = 0;
                }

                data.put("championDept", championDept);
                data.put("championPercent", Math.round(maxPercent * 100));

                // 3. Health Score
                long onTimeCount = dailyReport.stream()
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().equals("EARLY LEAVE"))
                                .filter(d -> !d.getStatus().contains("LATE"))
                                .count();
                long totalPresentCalculated = dailyReport.stream()
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                || d.getStatus().contains("EARLY"))
                                .count();
                int healthScore = totalPresentCalculated > 0 ? (int) ((onTimeCount * 100) / totalPresentCalculated) : 0;
                data.put("healthScore", healthScore);

                // 4. Punctuality Stars & Streak
                List<Integer> monthList = java.util.Collections.singletonList(now.getMonthValue());
                List<MonthlySummaryDto> monthlyStats = reportService.getMonthlyReport(now.getYear(), monthList, null,
                                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();

                List<MonthlySummaryDto> punctualityStars = monthlyStats.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .sorted(Comparator.comparingInt(MonthlySummaryDto::getPresentCount).reversed()
                                                .thenComparingInt(MonthlySummaryDto::getLateCount))
                                .limit(5)
                                .collect(Collectors.toList());
                data.put("punctualityStars", punctualityStars);

                MonthlySummaryDto streakTop = punctualityStars.isEmpty() ? null : punctualityStars.get(0);
                data.put("streakEmployee", streakTop);
        }
}
