package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ReportController {

    @Autowired
    private ReportService reportService;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.DepartmentRepository departmentRepository;

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            Model model) {
        if (date == null) {
            date = LocalDate.now();
        }
        model.addAttribute("date", date);
        model.addAttribute("report", reportService.getDailyReport(date, departmentId));
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedDeptId", departmentId);
        return "reports";
    }

    @GetMapping("/reports/weekly")
    public String weeklyReports(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            Model model) {
        if (date == null) {
            date = LocalDate.now();
        }
        // Adjust to start of week (Monday)
        LocalDate startOfWeek = date
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        // Generate headers (Mon 12, Tue 13...)
        List<String> headers = new java.util.ArrayList<>();
        LocalDate current = startOfWeek;
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("EEE dd");
        for (int i = 0; i < 7; i++) {
            headers.add(current.format(formatter));
            current = current.plusDays(1);
        }

        model.addAttribute("startOfWeek", startOfWeek);
        model.addAttribute("headers", headers);
        model.addAttribute("weekDates",
                startOfWeek.datesUntil(startOfWeek.plusDays(7)).collect(java.util.stream.Collectors.toList()));

        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedDept", departmentId);

        model.addAttribute("report", reportService.getWeeklyReport(startOfWeek, departmentId));
        return "reports-weekly";
    }

    @GetMapping("/reports/weekly/{employeeId}")
    public String employeeWeeklyReport(@org.springframework.web.bind.annotation.PathVariable String employeeId,
            @RequestParam(required = false) LocalDate date,
            Model model) {
        if (date == null)
            date = LocalDate.now();
        LocalDate startOfWeek = date
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        model.addAttribute("report", reportService.getEmployeeWeeklyReport(employeeId, startOfWeek));
        return "reports-employee-weekly";
    }

    @GetMapping("/reports/monthly")
    public String monthlyReports(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long departmentId,
            Model model) {
        if (year == null || month == null) {
            LocalDate now = LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        }

        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedDept", departmentId);

        model.addAttribute("report", reportService.getMonthlyReport(year, month, departmentId));
        return "reports-monthly";
    }

    @GetMapping("/reports/monthly/{employeeId}")
    public String employeeMonthlyReport(@org.springframework.web.bind.annotation.PathVariable String employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        if (year == null || month == null) {
            LocalDate now = LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        }

        model.addAttribute("report", reportService.getEmployeeMonthlyReport(employeeId, year, month));
        return "reports-employee-monthly";
    }
}
