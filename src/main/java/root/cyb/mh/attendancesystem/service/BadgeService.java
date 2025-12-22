package root.cyb.mh.attendancesystem.service;

import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BadgeService {

    @org.springframework.beans.factory.annotation.Autowired
    private root.cyb.mh.attendancesystem.repository.WorkScheduleRepository workScheduleRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private root.cyb.mh.attendancesystem.repository.PublicHolidayRepository publicHolidayRepository;

    public List<String> calculateBadges(EmployeeMonthlyDetailDto monthlyStats, List<DailyAttendanceDto> dailyLogs) {
        List<String> badges = new ArrayList<>();

        if (monthlyStats == null)
            return badges;

        // Calculate Working Days So Far (1st to Today)
        LocalDate now = LocalDate.now();
        int year = monthlyStats.getYear();
        int month = monthlyStats.getMonth();

        // If report is not for current month, use full month. If current, use up to
        // today.
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = (year == now.getYear() && month == now.getMonthValue()) ? now
                : start.withDayOfMonth(start.lengthOfMonth());

        int workingDaysSoFar = calculateWorkingDays(start, end);
        if (workingDaysSoFar == 0)
            return badges; // Avoid division by zero

        double attendancePct = (double) monthlyStats.getTotalPresent() / workingDaysSoFar;

        // Badge: "Iron Man" (100% Attendance so far)
        // Must be present every working day AND 0 Lates
        if (monthlyStats.getTotalPresent() >= workingDaysSoFar && monthlyStats.getTotalLates() == 0) {
            badges.add("iron_man");
        }

        // Badge: "Punctual Pro" (>85% Present, 0 Lates)
        if (attendancePct >= 0.85 && monthlyStats.getTotalLates() == 0) {
            if (!badges.contains("iron_man")) {
                badges.add("punctual_pro");
            }
        }

        // Badge: "Dedication" (>90% Present, Lates allowed)
        if (attendancePct >= 0.90) {
            if (!badges.contains("iron_man")) { // Iron man implies this too
                badges.add("dedication");
            }
        }

        return badges;
    }

    private int calculateWorkingDays(LocalDate start, LocalDate end) {
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
}
