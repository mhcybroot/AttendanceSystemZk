package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletResponse;

import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;
import root.cyb.mh.attendancesystem.service.PdfExportService;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Controller
public class EmployeeDashboardController {

    @Autowired
    private root.cyb.mh.attendancesystem.service.DashboardService dashboardService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.AttendanceRemarkRepository attendanceRemarkRepository;

    @GetMapping("/employee/dashboard")
    public String dashboard(Model model, Principal principal) {
        String employeeId = principal.getName();

        // Use the shared service to fetch all dashboard data
        Map<String, Object> data = dashboardService.getDashboardData(employeeId);
        model.addAllAttributes(data);

        return "employee-dashboard";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.repository.AssetRepository assetRepository;

    @GetMapping("/employee/assets")
    public String myAssets(Model model, Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        List<Asset> myAssets = assetRepository.findByAssignedTo(employee);

        model.addAttribute("assets", myAssets);
        model.addAttribute("activeLink", "my-assets");

        return "employee-assets";
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

    @PostMapping("/employee/profile/upload")
    public String uploadProfilePicture(@RequestParam("file") MultipartFile file, Principal principal) {
        if (!file.isEmpty()) {
            try {
                String uploadDir = "uploads/";
                java.io.File directory = new java.io.File(uploadDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path path = Paths.get(uploadDir + fileName);
                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

                String employeeId = principal.getName();
                Employee employee = employeeRepository.findById(employeeId).orElse(null);
                if (employee != null) {
                    employee.setAvatarPath("/uploads/" + fileName);
                    employeeRepository.save(employee);
                }
            } catch (IOException e) {
                e.printStackTrace(); // Handle error gracefully in real app
            }
        }
        return "redirect:/employee/dashboard";
    }

    @GetMapping("/employee/change-password")
    public String changePasswordForm(Model model, Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(new Employee());
        model.addAttribute("employee", employee);
        return "employee-change-password";
    }

    @PostMapping("/employee/change-password")
    public String changePasswordSubmit(
            @RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            Principal principal) {

        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        if (employee == null) {
            return "redirect:/login";
        }

        // 1. Verify Old Password
        // Note: In Employee model, 'username' field stores the BCrypt hashed password
        if (!passwordEncoder.matches(oldPassword, employee.getUsername())) {
            redirectAttributes.addFlashAttribute("error", "Incorrect old password.");
            return "redirect:/employee/change-password";
        }

        // 2. new vs confirm
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/employee/change-password";
        }

        // 3. Update
        employee.setUsername(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);

        redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        return "redirect:/employee/dashboard";
    }

    @PostMapping("/employee/attendance/note")
    public String addAttendanceNote(
            @RequestParam("date") String dateStr,
            @RequestParam("note") String note,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        String employeeId = principal.getName();
        LocalDate date = LocalDate.parse(dateStr);

        root.cyb.mh.attendancesystem.model.AttendanceRemark remark = attendanceRemarkRepository
                .findByEmployeeIdAndDate(employeeId, date)
                .orElse(new root.cyb.mh.attendancesystem.model.AttendanceRemark());

        if (remark.getId() == null) {
            remark.setEmployeeId(employeeId);
            remark.setDate(date);
        }
        remark.setNote(note);
        attendanceRemarkRepository.save(remark);

        redirectAttributes.addFlashAttribute("success", "Note added successfully.");

        String redirectUrl = "redirect:/employee/attendance/history";
        if (year != null && month != null) {
            redirectUrl += "?year=" + year + "&month=" + month;
        }
        return redirectUrl;
    }
}
