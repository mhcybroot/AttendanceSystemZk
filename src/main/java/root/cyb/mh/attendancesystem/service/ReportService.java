package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.WorkSchedule;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.PublicHolidayRepository publicHolidayRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private ShiftService shiftService;

    public Page<DailyAttendanceDto> getDailyReport(LocalDate date, List<Long> departmentIds, String statusFilter,
            Pageable pageable) {

        List<DailyAttendanceDto> report = new ArrayList<>();

        // Get Work Schedule
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());

        // Get Employees (Filter by Dept if provided)
        List<Employee> allFilteredEmployees;
        if (departmentIds != null && !departmentIds.isEmpty()) {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> !e.isGuest()) // Exclude Guests
                    .filter(e -> e.getDepartment() != null && departmentIds.contains(e.getDepartment().getId()))
                    .collect(Collectors.toList());
        } else {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> !e.isGuest()) // Exclude Guests
                    .collect(Collectors.toList());
        }

        // Generate Report Data
        List<DailyAttendanceDto> fullReport = generateDailyReportData(allFilteredEmployees, date, globalSchedule);

        // Apply Status Filter
        if (statusFilter != null && !statusFilter.isEmpty()) {
            fullReport = fullReport.stream().filter(dto -> {
                if ("PRESENT".equalsIgnoreCase(statusFilter)) {
                    return dto.getStatus().contains("PRESENT") || dto.getStatus().contains("LATE")
                            || dto.getStatus().contains("EARLY");
                }
                if ("ABSENT".equalsIgnoreCase(statusFilter)) {
                    return dto.getStatus().contains("ABSENT");
                }
                if ("LEAVE".equalsIgnoreCase(statusFilter)) {
                    return dto.getStatus().contains("LEAVE") && !dto.getStatus().contains("EARLY");
                }
                if ("LATE".equalsIgnoreCase(statusFilter)) {
                    return dto.getStatus().contains("LATE");
                }
                return true;
            }).collect(Collectors.toList());
        }

        // Pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), fullReport.size());
        List<DailyAttendanceDto> pagedContent = new ArrayList<>();
        if (start <= fullReport.size()) {
            pagedContent = fullReport.subList(start, end);
        }

        return new PageImpl<>(pagedContent, pageable, fullReport.size());
    }

    public List<DailyAttendanceDto> getTeamDailyStatus(List<Employee> teamMembers) {
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        // Use today's date
        return generateDailyReportData(teamMembers, LocalDate.now(), globalSchedule);
    }

    private List<DailyAttendanceDto> generateDailyReportData(List<Employee> employees, LocalDate date,
            WorkSchedule globalSchedule) {
        List<DailyAttendanceDto> report = new ArrayList<>();
        List<AttendanceLog> logs = attendanceLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().toLocalDate().equals(date))
                .collect(Collectors.toList());

        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> approvedLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED)
                .stream()
                .filter(l -> !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()))
                .collect(Collectors.toList());

        for (Employee emp : employees) {
            DailyAttendanceDto dto = new DailyAttendanceDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

            // Helpful for UI
            dto.setDesignation(emp.getDesignation());
            dto.setAvatarPath(emp.getAvatarPath());

            WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

            List<AttendanceLog> empLogs = logs.stream()
                    .filter(log -> log.getEmployeeId().equals(emp.getId()))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            if (emp.getJoiningDate() != null && date.isBefore(emp.getJoiningDate())) {
                dto.setStatus("NOT JOINED");
                dto.setStatusColor("secondary");
                report.add(dto);
                continue;
            }

            if (isWeekend || isPublicHoliday) {
                dto.setStatus("WEEKEND/HOLIDAY");
                dto.setStatusColor("secondary");
                if (!empLogs.isEmpty()) {
                    dto.setStatus("PRESENT (HOLIDAY)");
                    dto.setStatusColor("success");
                    dto.setInTime(empLogs.get(0).getTimestamp().toLocalTime());
                    dto.setOutTime(empLogs.get(empLogs.size() - 1).getTimestamp().toLocalTime());
                }
            } else if (empLogs.isEmpty()) {
                boolean onLeave = approvedLeaves.stream().anyMatch(l -> l.getEmployee().getId().equals(emp.getId()));
                if (onLeave) {
                    root.cyb.mh.attendancesystem.model.LeaveRequest leave = approvedLeaves.stream()
                            .filter(l -> l.getEmployee().getId().equals(emp.getId()))
                            .findFirst().orElse(null);
                    String type = leave != null && leave.getLeaveType() != null ? leave.getLeaveType().toUpperCase()
                            : "";
                    dto.setStatus(!type.isEmpty() ? type + " LEAVE" : "ON LEAVE");
                    dto.setStatusColor("info");
                } else {
                    dto.setStatus("ABSENT");
                    dto.setStatusColor("danger");
                }
            } else {
                LocalTime inTime = empLogs.get(0).getTimestamp().toLocalTime();
                LocalTime outTime = empLogs.get(empLogs.size() - 1).getTimestamp().toLocalTime();
                dto.setInTime(inTime);
                dto.setOutTime(outTime);

                LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                LocalTime earlyThreshold = schedule.getEndTime().minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                boolean isLate = inTime.isAfter(lateThreshold);
                boolean isEarly = outTime.isBefore(earlyThreshold);

                if (isLate && isEarly) {
                    dto.setStatus("LATE & EARLY LEAVE");
                    dto.setStatusColor("warning");
                } else if (isLate) {
                    dto.setStatus("LATE ENTRY");
                    dto.setStatusColor("warning");
                } else if (isEarly) {
                    dto.setStatus("EARLY LEAVE");
                    dto.setStatusColor("info");
                } else {
                    dto.setStatus("PRESENT");
                    dto.setStatusColor("success");
                }
            }
            report.add(dto);
        }
        return report;

    }

    public Page<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> getWeeklyReport(LocalDate startOfWeek,
            List<Long> departmentIds, Pageable pageable) {
        // Ensure start date works
        if (startOfWeek == null)
            startOfWeek = LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        LocalDate endOfWeek = startOfWeek.plusDays(6);
        List<LocalDate> weekDates = startOfWeek.datesUntil(endOfWeek.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        List<Employee> allFilteredEmployees;
        if (departmentIds != null && !departmentIds.isEmpty()) {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && departmentIds.contains(e.getDepartment().getId()))
                    .collect(Collectors.toList());
        } else {
            allFilteredEmployees = employeeRepository.findAll();
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFilteredEmployees.size());
        List<Employee> employees = new ArrayList<>();
        if (start <= allFilteredEmployees.size()) {
            employees = allFilteredEmployees.subList(start, end);
        }

        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX));

        // Get Approved Leaves overlapping the week
        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> report = new ArrayList<>();

        for (Employee emp : employees) {
            root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto dto = new root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
            dto.setDailyStatus(new java.util.LinkedHashMap<>());

            int present = 0, absent = 0, late = 0, early = 0, leave = 0;

            for (LocalDate date : weekDates) {
                WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

                // Check if Holiday/Weekend
                boolean isWeekend = schedule.getWeekendDays() != null
                        && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
                boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

                String status = "";

                if (emp.getJoiningDate() != null && date.isBefore(emp.getJoiningDate())) {
                    status = "NOT JOINED";
                    dto.getDailyStatus().put(date, status);
                    continue;
                }

                // Get logs for this emp & date
                List<AttendanceLog> dailyLogs = allLogs.stream()
                        .filter(l -> l.getEmployeeId().equals(emp.getId())
                                && l.getTimestamp().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                        .collect(Collectors.toList());

                if (isWeekend || isPublicHoliday) {
                    status = "WEEKEND"; // or HOLIDAY
                    // If they came anyway, count as Present
                    if (!dailyLogs.isEmpty()) {
                        status = "PRESENT";
                        present++;

                        LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                        LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();

                        LocalTime lateThreshold = schedule.getStartTime()
                                .plusMinutes(schedule.getLateToleranceMinutes());
                        LocalTime earlyThreshold = schedule.getEndTime()
                                .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                        if (inTime.isAfter(lateThreshold)) {
                            status = "LATE";
                            late++;
                        }
                        if (outTime.isBefore(earlyThreshold)) {
                            if (status.equals("LATE"))
                                status = "LATE_EARLY";
                            else
                                status = "EARLY";
                            early++;
                        }
                    }
                } else if (dailyLogs.isEmpty()) {
                    // Check Leave
                    boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
                    if (onLeave) {
                        status = "LEAVE";
                        // Don't increment absent/present count for leave? Or separate count?
                        // But for Summary PDF we might want to know.
                        // Let's rely on string status for now.
                        leave++;
                    } else {
                        status = "ABSENT";
                        absent++;
                    }
                } else {
                    status = "PRESENT";
                    present++;

                    LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();

                    LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                    LocalTime earlyThreshold = schedule.getEndTime()
                            .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                    if (inTime.isAfter(lateThreshold)) {
                        status = "LATE"; // Simplified for grid
                        late++;
                    }
                    if (outTime.isBefore(earlyThreshold)) {
                        if (status.equals("LATE"))
                            status = "LATE_EARLY";
                        else
                            status = "EARLY";
                        early++;
                    }
                }
                dto.getDailyStatus().put(date, status);
            }
            dto.setPresentCount(present);
            dto.setAbsentCount(absent);
            dto.setLateCount(late);
            dto.setEarlyLeaveCount(early);
            dto.setLeaveCount(leave);
            report.add(dto);
        }
        return new PageImpl<>(report, pageable, allFilteredEmployees.size());
    }

    public root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto getEmployeeWeeklyReport(String employeeId,
            LocalDate startOfWeek) {
        root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto dto = new root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto();

        // Find Employee
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null)
            return null;

        dto.setEmployeeName(emp.getName());
        dto.setEmployeeId(emp.getId());
        dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

        // Dates
        if (startOfWeek == null)
            startOfWeek = LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        LocalDate endOfWeek = startOfWeek.plusDays(6);
        dto.setStartOfWeek(startOfWeek);
        dto.setEndOfWeek(endOfWeek);

        List<LocalDate> weekDates = startOfWeek.datesUntil(endOfWeek.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> details = new ArrayList<>();
        int present = 0, absent = 0, late = 0, early = 0, leaves = 0;

        for (LocalDate date : weekDates) {
            WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);
            root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail daily = new root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail();
            daily.setDate(date);
            daily.setDayOfWeek(date.getDayOfWeek().name());

            // Check keys
            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            // Logs
            List<AttendanceLog> dailyLogs = allLogs.stream()
                    .filter(l -> l.getEmployeeId().equals(emp.getId())
                            && l.getTimestamp().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            String status = "";
            String color = "";

            if (emp.getJoiningDate() != null && date.isBefore(emp.getJoiningDate())) {
                daily.setStatus("NOT JOINED");
                daily.setStatusColor("secondary");
                details.add(daily);
                continue;
            }

            if (isWeekend || isPublicHoliday) {
                status = "WEEKEND";
                color = "secondary";
                if (isPublicHoliday)
                    status = "HOLIDAY";

                if (!dailyLogs.isEmpty()) {
                    status = "PRESENT (" + status + ")";
                    color = "success";
                    present++;

                    // Calc timings
                    processTimings(daily, dailyLogs, schedule);
                    // Check late/early but don't strictly flag as 'LATE' stats if it's a holiday,
                    // unless we want to track overtime strictness. Let's just show times.
                }
            } else if (dailyLogs.isEmpty()) {
                boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
                if (onLeave) {
                    status = "LEAVE";
                    color = "info";
                    leaves++;
                } else {
                    status = "ABSENT";
                    color = "danger";
                    absent++;
                }
            } else {
                status = "PRESENT";
                color = "success";
                present++;

                processTimings(daily, dailyLogs, schedule);

                if (daily.getLateDurationMinutes() > 0) {
                    late++;
                    if (status.equals("PRESENT"))
                        status = "LATE";
                }
                if (daily.getEarlyLeaveDurationMinutes() > 0) {
                    early++;
                    if (status.equals("PRESENT"))
                        status = "EARLY";
                    else if (status.equals("LATE"))
                        status = "LATE & EARLY";
                }

                // Colors for issues
                if (status.contains("LATE") || status.contains("EARLY")) {
                    if (status.contains("&"))
                        color = "warning"; // Late & Early
                    else if (status.contains("LATE"))
                        color = "warning";
                    else
                        color = "info"; // Early
                }
            }

            daily.setStatus(status);
            daily.setStatusColor(color);
            details.add(daily);
        }

        dto.setDailyDetails(details);
        dto.setTotalPresent(present);
        dto.setTotalAbsent(absent);
        dto.setTotalLates(late);
        dto.setTotalEarlyLeaves(early);

        return dto;
    }

    private void processTimings(root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail daily,
            List<AttendanceLog> logs, WorkSchedule schedule) {
        LocalTime inTime = logs.get(0).getTimestamp().toLocalTime();
        LocalTime outTime = logs.get(logs.size() - 1).getTimestamp().toLocalTime();

        daily.setInTime(inTime);
        daily.setOutTime(outTime);

        // Thresholds
        LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
        LocalTime earlyThreshold = schedule.getEndTime().minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

        if (inTime.isAfter(lateThreshold)) {
            java.time.Duration diff = java.time.Duration.between(schedule.getStartTime(), inTime);
            // We count lateness from the Start Time, not the threshold
            daily.setLateDurationMinutes(diff.toMinutes());
        }

        if (outTime.isBefore(earlyThreshold)) {
            java.time.Duration diff = java.time.Duration.between(outTime, schedule.getEndTime());
            daily.setEarlyLeaveDurationMinutes(diff.toMinutes());
        }
    }

    public Page<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> getMonthlyReport(int year, List<Integer> months,
            List<Long> departmentIds, Pageable pageable) {
        List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> fullReport = new ArrayList<>();

        // If no month selected, default to current (or 1?) - let's default to current
        // if
        // null/empty
        if (months == null || months.isEmpty()) {
            months = new ArrayList<>();
            months.add(LocalDate.now().getMonthValue());
        }

        // Configs (Fetch once)
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        int defaultQuota = globalSchedule.getDefaultAnnualLeaveQuota() != null
                ? globalSchedule.getDefaultAnnualLeaveQuota()
                : 12;

        // Filter Employees (Fetch once)
        List<Employee> allFilteredEmployees;
        if (departmentIds != null && !departmentIds.isEmpty()) {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && departmentIds.contains(e.getDepartment().getId()))
                    .collect(Collectors.toList());
        } else {
            allFilteredEmployees = employeeRepository.findAll();
        }

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        // Iterate Months
        for (Integer month : months) {
            LocalDate startOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
            List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

            // Fetch Logs for THIS month
            List<AttendanceLog> monthLogs = attendanceLogRepository.findByTimestampBetween(
                    startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX));

            for (Employee emp : allFilteredEmployees) {
                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto dto = new root.cyb.mh.attendancesystem.dto.MonthlySummaryDto();
                dto.setEmployeeId(emp.getId());
                dto.setEmployeeName(emp.getName());
                dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
                dto.setMonth(month); // Set Month
                dto.setYear(year); // Set Year

                int present = 0, absent = 0, late = 0, early = 0, leave = 0;
                int paidLeave = 0, unpaidLeave = 0;

                // Quota Calc for this month context
                int effectiveQuota = emp.getEffectiveQuota(defaultQuota);
                int leavesTakenBefore = countYearlyLeavesBeforeMonth(emp.getId(), year, month, allLeaves);
                int remainingQuota = Math.max(0, effectiveQuota - leavesTakenBefore);

                for (LocalDate date : monthDates) {
                    WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);
                    boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);

                    if (onLeave) {
                        leave++;
                        if (remainingQuota > 0) {
                            paidLeave++;
                            remainingQuota--;
                        } else {
                            unpaidLeave++;
                        }
                        continue;
                    }

                    boolean isWeekend = schedule.getWeekendDays() != null
                            && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
                    boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

                    List<AttendanceLog> dailyLogs = monthLogs.stream()
                            .filter(l -> l.getEmployeeId().equals(emp.getId())
                                    && l.getTimestamp().toLocalDate().equals(date))
                            .collect(Collectors.toList());

                    if (emp.getJoiningDate() != null && date.isBefore(emp.getJoiningDate())) {
                        // Skip checking attendance for days before joining
                        continue;
                    }

                    if (!dailyLogs.isEmpty()) {
                        present++;
                        if (!isWeekend && !isPublicHoliday) {
                            LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                            LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();
                            LocalTime lateThreshold = schedule.getStartTime()
                                    .plusMinutes(schedule.getLateToleranceMinutes());
                            LocalTime earlyThreshold = schedule.getEndTime()
                                    .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                            if (inTime.isAfter(lateThreshold))
                                late++;
                            if (outTime.isBefore(earlyThreshold))
                                early++;
                        }
                    } else {
                        if (!isWeekend && !isPublicHoliday)
                            absent++;
                    }
                }
                dto.setPresentCount(present);
                dto.setAbsentCount(absent);
                dto.setLateCount(late);
                dto.setEarlyLeaveCount(early);
                dto.setLeaveCount(leave);
                dto.setPaidLeaveCount(paidLeave);
                dto.setUnpaidLeaveCount(unpaidLeave);
                fullReport.add(dto);
            }
        }

        // Pagination on the aggregated list
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), fullReport.size());
        List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> pagedContent = new ArrayList<>();
        if (start <= fullReport.size()) {
            pagedContent = fullReport.subList(start, end);
        }

        return new PageImpl<>(pagedContent, pageable, fullReport.size());
    }

    public root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto getEmployeeMonthlyReport(String employeeId,
            int year, int month) {
        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto dto = new root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto();

        // Find Employee
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null)
            return null;

        dto.setEmployeeName(emp.getName());
        dto.setEmployeeId(emp.getId());
        dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
        dto.setYear(year);
        dto.setMonth(month);

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        int defaultQuota = globalSchedule.getDefaultAnnualLeaveQuota() != null
                ? globalSchedule.getDefaultAnnualLeaveQuota()
                : 12;

        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> details = new ArrayList<>();
        int present = 0, absent = 0, late = 0, early = 0, leaves = 0;
        int paidLeaves = 0, unpaidLeaves = 0;

        // Calculate Remaining Quota
        int effectiveQuota = emp.getEffectiveQuota(defaultQuota);
        int leavesTakenBefore = countYearlyLeavesBeforeMonth(emp.getId(), year, month, allLeaves);
        int remainingQuota = Math.max(0, effectiveQuota - leavesTakenBefore);

        for (LocalDate date : monthDates) {
            WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

            root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail daily = new root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail();
            daily.setDate(date);
            daily.setDayOfWeek(date.getDayOfWeek().name());

            if (emp.getJoiningDate() != null && date.isBefore(emp.getJoiningDate())) {
                daily.setStatus("NOT JOINED");
                daily.setStatusColor("secondary");
                details.add(daily);
                continue;
            }

            // Priority 1: Check Leave FIRST overrides everything
            boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
            if (onLeave) {
                leaves++;
                String leaveType = remainingQuota > 0 ? "PAID LEAVE" : "UNPAID LEAVE";
                if (remainingQuota > 0) {
                    paidLeaves++;
                    remainingQuota--;
                } else {
                    unpaidLeaves++;
                }

                daily.setStatus(leaveType);
                daily.setStatusColor("info");
                details.add(daily);
                continue; // Skip further processing for this day
            }

            // Check keys
            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            // Logs
            List<AttendanceLog> dailyLogs = allLogs.stream()
                    .filter(l -> l.getEmployeeId().equals(emp.getId())
                            && l.getTimestamp().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            String status = "";
            String color = "";

            if (!dailyLogs.isEmpty()) {
                // Priority 2: PRESENT
                present++;
                status = "PRESENT";
                color = "success";

                processTimings(daily, dailyLogs, schedule); // calculates late/early minutes

                if (isWeekend || isPublicHoliday) {
                    status = "PRESENT (" + (isPublicHoliday ? "HOLIDAY" : "WEEKEND") + ")";
                } else {
                    // Working Day: Update Counters and Status Label for deviations
                    if (daily.getLateDurationMinutes() > 0) {
                        late++;
                        status = "LATE";
                    }
                    if (daily.getEarlyLeaveDurationMinutes() > 0) {
                        early++;
                        if (status.equals("PRESENT"))
                            status = "EARLY";
                        else if (status.equals("LATE"))
                            status = "LATE & EARLY";
                    }

                    if (status.contains("LATE") || status.contains("EARLY")) {
                        if (status.contains("&"))
                            color = "warning";
                        else if (status.contains("LATE"))
                            color = "warning";
                        else
                            color = "info";
                    }
                }
            } else {
                // Priority 3: NO LOGS
                if (isWeekend || isPublicHoliday) {
                    // WEEKEND/HOLIDAY
                    status = isPublicHoliday ? "HOLIDAY" : "WEEKEND";
                    color = "secondary";
                } else {
                    // Priority 4: ABSENT
                    status = "ABSENT";
                    color = "danger";
                    absent++;
                }
            }

            daily.setStatus(status);
            daily.setStatusColor(color);
            details.add(daily);
        }

        dto.setDailyDetails(details);
        dto.setTotalPresent(present);
        dto.setTotalAbsent(absent);
        dto.setTotalLates(late);
        dto.setTotalEarlyLeaves(early);
        dto.setTotalLeaves(leaves);
        dto.setPaidLeavesCount(paidLeaves);
        dto.setUnpaidLeavesCount(unpaidLeaves);

        return dto;
    }

    public int countYearlyLeavesBeforeMonth(String employeeId, int year, int month,
            List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate startOfMonth = LocalDate.of(year, month, 1);

        return (int) allLeaves.stream()
                .filter(l -> l.getEmployee().getId().equals(employeeId))
                .filter(l -> !l.getStartDate().isBefore(startOfYear) && l.getStartDate().isBefore(startOfMonth))
                .mapToLong(l -> java.time.temporal.ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1)
                .sum();
    }

    // Helper to calculate Working Days (Total - Weekends - Holidays)
    public int calculateWorkingDays(LocalDate start, LocalDate end) {
        // 1. Get Global Schedule (Weekend Config)
        root.cyb.mh.attendancesystem.model.WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst()
                .orElse(null);
        List<String> weekendDays = new ArrayList<>();
        if (schedule != null && schedule.getWeekendDays() != null && !schedule.getWeekendDays().isEmpty()) {
            String[] days = schedule.getWeekendDays().split(",");
            for (String d : days) {
                weekendDays.add(d.trim());
            }
        } else {
            weekendDays.add("6"); // Default Sat
            weekendDays.add("7"); // Default Sun
        }

        // 2. Get Holidays in Range
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        int workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            // Check Weekend
            String dayOfWeek = String.valueOf(current.getDayOfWeek().getValue()); // 1=Mon, 7=Sun
            boolean isWeekend = weekendDays.contains(dayOfWeek);

            // Check Holiday (Single Date Check)
            LocalDate finalCurrent = current;
            boolean isHoliday = holidays.stream()
                    .anyMatch(h -> h.getDate() != null && h.getDate().equals(finalCurrent));

            if (!isWeekend && !isHoliday) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    public boolean isEmployeeOnLeave(String employeeId, LocalDate date,
            List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves) {
        return allLeaves.stream()
                .anyMatch(l -> l.getEmployee().getId().equals(employeeId) &&
                        !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()));
    }

    public root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto getEmployeeRangeReport(
            String employeeId, LocalDate startDate, LocalDate endDate) {

        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeDto = new root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto();
        rangeDto.setEmployeeId(employeeId);
        rangeDto.setStartDate(startDate);
        rangeDto.setEndDate(endDate);

        // Find Employee for basic info
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp != null) {
            rangeDto.setEmployeeName(emp.getName());
            rangeDto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
        }

        List<root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto> monthlyReports = new ArrayList<>();

        LocalDate current = startDate.withDayOfMonth(1);
        LocalDate endLoop = endDate.withDayOfMonth(1); // Compare months

        while (!current.isAfter(endLoop)) {
            int y = current.getYear();
            int m = current.getMonthValue();

            root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto monthDto = getEmployeeMonthlyReport(employeeId, y,
                    m);
            if (monthDto != null) {
                monthlyReports.add(monthDto);

                // Aggregate Stats
                rangeDto.setTotalPresent(rangeDto.getTotalPresent() + monthDto.getTotalPresent());
                rangeDto.setTotalAbsent(rangeDto.getTotalAbsent() + monthDto.getTotalAbsent());
                rangeDto.setTotalLates(rangeDto.getTotalLates() + monthDto.getTotalLates());
                rangeDto.setTotalEarlyLeaves(rangeDto.getTotalEarlyLeaves() + monthDto.getTotalEarlyLeaves());
                rangeDto.setTotalLeaves(rangeDto.getTotalLeaves() + monthDto.getTotalLeaves());
                rangeDto.setTotalPaidLeaves(rangeDto.getTotalPaidLeaves() + monthDto.getPaidLeavesCount());
                rangeDto.setTotalUnpaidLeaves(rangeDto.getTotalUnpaidLeaves() + monthDto.getUnpaidLeavesCount());
            }

            current = current.plusMonths(1);
        }

        rangeDto.setMonthlyReports(monthlyReports);
        return rangeDto;
    }

    public WorkSchedule resolveSchedule(String employeeId, LocalDate date, WorkSchedule globalDefault) {
        root.cyb.mh.attendancesystem.model.Shift specificShift = shiftService.getShiftForDate(employeeId, date);
        if (specificShift == null) {
            return globalDefault;
        }
        WorkSchedule effective = new WorkSchedule();
        effective.setStartTime(specificShift.getStartTime());
        effective.setEndTime(specificShift.getEndTime());
        effective.setLateToleranceMinutes(specificShift.getLateToleranceMinutes());
        effective.setEarlyLeaveToleranceMinutes(specificShift.getEarlyLeaveToleranceMinutes());
        effective.setWeekendDays(globalDefault.getWeekendDays());
        effective.setDefaultAnnualLeaveQuota(globalDefault.getDefaultAnnualLeaveQuota());
        return effective;
    }
}
