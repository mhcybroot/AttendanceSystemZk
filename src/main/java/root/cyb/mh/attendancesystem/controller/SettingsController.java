package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import root.cyb.mh.attendancesystem.model.WorkSchedule;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;
import java.util.List;

@Controller
public class SettingsController {

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.PublicHolidayRepository publicHolidayRepository;

    @GetMapping("/settings")
    public String settings(Model model) {
        // We assume only one global schedule for now. Get the first one or create it.
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        if (schedule.getId() == null) {
            workScheduleRepository.save(schedule); // Initialize if empty
        }
        model.addAttribute("schedule", schedule);
        model.addAttribute("holidays",
                publicHolidayRepository.findAll(org.springframework.data.domain.Sort.by("date")));
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@ModelAttribute WorkSchedule schedule,
            @RequestParam(required = false) List<Integer> weekendDaysList) {
        WorkSchedule existing = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        if (schedule.getStartTime() != null)
            existing.setStartTime(schedule.getStartTime());
        if (schedule.getEndTime() != null)
            existing.setEndTime(schedule.getEndTime());
        existing.setLateToleranceMinutes(schedule.getLateToleranceMinutes());
        existing.setEarlyLeaveToleranceMinutes(schedule.getEarlyLeaveToleranceMinutes());

        // Convert list [6, 7] to string "6,7"
        if (weekendDaysList != null) {
            existing.setWeekendDays(
                    String.join(",", weekendDaysList.stream().map(String::valueOf).toArray(String[]::new)));
        } else {
            existing.setWeekendDays("");
        }

        workScheduleRepository.save(existing);
        return "redirect:/settings?success";
    }

    @PostMapping("/settings/holidays/add")
    public String addHoliday(@RequestParam String name, @RequestParam java.time.LocalDate date) {
        root.cyb.mh.attendancesystem.model.PublicHoliday holiday = new root.cyb.mh.attendancesystem.model.PublicHoliday();
        holiday.setName(name);
        holiday.setDate(date);
        publicHolidayRepository.save(holiday);
        return "redirect:/settings";
    }

    @GetMapping("/settings/holidays/delete")
    public String deleteHoliday(@RequestParam Long id) {
        publicHolidayRepository.deleteById(id);
        return "redirect:/settings";
    }
}
