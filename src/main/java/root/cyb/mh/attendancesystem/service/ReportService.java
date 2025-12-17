package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
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

    public List<DailyAttendanceDto> getDailyReport(LocalDate date, Long departmentId) {
        List<DailyAttendanceDto> report = new ArrayList<>();

        // Get Work Schedule
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());

        // Get Employees (Filter by Dept if provided)
        List<Employee> employees;
        if (departmentId != null) {
            employees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        } else {
            employees = employeeRepository.findAll();
        }

        // Get All Logs for the Date
        List<AttendanceLog> logs = attendanceLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().toLocalDate().equals(date))
                .collect(Collectors.toList());

        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        for (Employee emp : employees) {
            DailyAttendanceDto dto = new DailyAttendanceDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

            // Filter logs for this employee
            List<AttendanceLog> empLogs = logs.stream()
                    .filter(log -> log.getEmployeeId().equals(emp.getId()))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            if (isWeekend || isPublicHoliday) {
                dto.setStatus("WEEKEND/HOLIDAY");
                dto.setStatusColor("secondary"); // Gray
                // If they came anyway, count as Present for daily view too
                if (!empLogs.isEmpty()) {
                    dto.setStatus("PRESENT (HOLIDAY)");
                    dto.setStatusColor("success");
                    LocalTime inTime = empLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime outTime = empLogs.get(empLogs.size() - 1).getTimestamp().toLocalTime();
                    dto.setInTime(inTime);
                    dto.setOutTime(outTime);
                }
            } else if (empLogs.isEmpty()) {
                dto.setStatus("ABSENT");
                dto.setStatusColor("danger");
            } else {
                LocalTime inTime = empLogs.get(0).getTimestamp().toLocalTime();
                LocalTime outTime = empLogs.get(empLogs.size() - 1).getTimestamp().toLocalTime();

                dto.setInTime(inTime);
                dto.setOutTime(outTime);

                // Check Status
                LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                LocalTime earlyThreshold = schedule.getEndTime().minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                boolean isLate = inTime.isAfter(lateThreshold);
                boolean isEarly = outTime.isBefore(earlyThreshold);

                if (isLate && isEarly) {
                    dto.setStatus("LATE & EARLY LEAVE");
                    dto.setStatusColor("warning"); // Orange-ish
                } else if (isLate) {
                    dto.setStatus("LATE ENTRY");
                    dto.setStatusColor("warning");
                } else if (isEarly) {
                    dto.setStatus("EARLY LEAVE");
                    dto.setStatusColor("info"); // Light blue
                } else {
                    dto.setStatus("PRESENT");
                    dto.setStatusColor("success");
                }
            }
            report.add(dto);
        }

        return report;
    }

    public List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> getWeeklyReport(LocalDate startOfWeek,
            Long departmentId) {
        List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> report = new ArrayList<>();

        // Ensure start date works
        if (startOfWeek == null)
            startOfWeek = LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        LocalDate endOfWeek = startOfWeek.plusDays(6);
        List<LocalDate> weekDates = startOfWeek.datesUntil(endOfWeek.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        List<Employee> employees;
        if (departmentId != null) {
            employees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        } else {
            employees = employeeRepository.findAll();
        }

        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX));

        for (Employee emp : employees) {
            root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto dto = new root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
            dto.setDailyStatus(new java.util.LinkedHashMap<>());

            int present = 0, absent = 0, late = 0, early = 0;

            for (LocalDate date : weekDates) {
                // Check if Holiday/Weekend
                boolean isWeekend = schedule.getWeekendDays() != null
                        && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
                boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

                String status = "";

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
                    status = "ABSENT";
                    absent++;
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
            report.add(dto);
        }
        return report;
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
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> details = new ArrayList<>();
        int present = 0, absent = 0, late = 0, early = 0;

        for (LocalDate date : weekDates) {
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
                status = "ABSENT";
                color = "danger";
                absent++;
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

    public List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> getMonthlyReport(int year, int month,
            Long departmentId) {
        List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> report = new ArrayList<>();

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        // Filter Employees
        List<Employee> employees;
        if (departmentId != null) {
            employees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        } else {
            employees = employeeRepository.findAll();
        }

        // Fetch Logs
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX));

        for (Employee emp : employees) {
            root.cyb.mh.attendancesystem.dto.MonthlySummaryDto dto = new root.cyb.mh.attendancesystem.dto.MonthlySummaryDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

            int present = 0, absent = 0, late = 0, early = 0;

            for (LocalDate date : monthDates) {
                // Check keys
                boolean isWeekend = schedule.getWeekendDays() != null
                        && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
                boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

                // Logs for day
                List<AttendanceLog> dailyLogs = allLogs.stream()
                        .filter(l -> l.getEmployeeId().equals(emp.getId())
                                && l.getTimestamp().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                        .collect(Collectors.toList());

                if (isWeekend || isPublicHoliday) {
                    if (!dailyLogs.isEmpty()) {
                        present++;
                    }
                } else if (dailyLogs.isEmpty()) {
                    absent++;
                } else {
                    present++;

                    LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();

                    LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                    LocalTime earlyThreshold = schedule.getEndTime()
                            .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                    if (inTime.isAfter(lateThreshold)) {
                        late++;
                    }
                    if (outTime.isBefore(earlyThreshold)) {
                        early++;
                    }
                }
            }

            dto.setPresentCount(present);
            dto.setAbsentCount(absent);
            dto.setLateCount(late);
            dto.setEarlyLeaveCount(early);
            report.add(dto);
        }

        return report;
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
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> details = new ArrayList<>();
        int present = 0, absent = 0, late = 0, early = 0;

        for (LocalDate date : monthDates) {
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

            if (isWeekend || isPublicHoliday) {
                status = "WEEKEND";
                color = "secondary";
                if (isPublicHoliday)
                    status = "HOLIDAY";

                if (!dailyLogs.isEmpty()) {
                    status = "PRESENT (" + status + ")";
                    color = "success";
                    present++;
                    processTimings(daily, dailyLogs, schedule);
                }
            } else if (dailyLogs.isEmpty()) {
                status = "ABSENT";
                color = "danger";
                absent++;
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

                if (status.contains("LATE") || status.contains("EARLY")) {
                    if (status.contains("&"))
                        color = "warning";
                    else if (status.contains("LATE"))
                        color = "warning";
                    else
                        color = "info";
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
}
