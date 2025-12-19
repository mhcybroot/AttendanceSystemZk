package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.time.LocalDate;
import java.time.LocalTime;
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
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "name") String sortField,
                        @RequestParam(defaultValue = "asc") String sortDir,
                        Model model) {
                if (date == null) {
                        date = LocalDate.now();
                }

                // ... (Comments about sorting kept conceptual, skipping for brevity in
                // replacement if unchanged usually, but here I replace the block)

                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                size);

                org.springframework.data.domain.Page<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> reportPage = reportService
                                .getDailyReport(date, departmentId, status, pageable);

                // Sorting the *current page* content (DTOs)
                List<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> reportContent = new java.util.ArrayList<>(
                                reportPage.getContent());

                // ... Comparator logic ...
                java.util.Comparator<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> comparator = null;
                switch (sortField) {
                        case "department":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getDepartmentName() != null ? dto.getDepartmentName() : "");
                                break;
                        case "inTime":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getInTime() != null ? dto.getInTime() : LocalTime.MAX);
                                break;
                        case "outTime":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getOutTime() != null ? dto.getOutTime() : LocalTime.MIN);
                                break;
                        case "status":
                                comparator = java.util.Comparator.comparing(
                                                root.cyb.mh.attendancesystem.dto.DailyAttendanceDto::getStatus);
                                break;
                        case "name":
                        default:
                                comparator = java.util.Comparator.comparing(
                                                root.cyb.mh.attendancesystem.dto.DailyAttendanceDto::getEmployeeName);
                                break;
                }

                if ("desc".equalsIgnoreCase(sortDir)) {
                        comparator = comparator.reversed();
                }

                if (comparator != null) {
                        reportContent.sort(comparator);
                }

                model.addAttribute("report", reportContent);
                model.addAttribute("page", reportPage);

                model.addAttribute("date", date);
                model.addAttribute("departments", departmentRepository.findAll());
                model.addAttribute("selectedDeptId", departmentId);
                model.addAttribute("selectedStatus", status);

                model.addAttribute("sortField", sortField);
                model.addAttribute("sortDir", sortDir);
                model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

                return "reports";
        }

        // ... weeklyReports ...

        // ... monthlyReports ...

        @Autowired
        private root.cyb.mh.attendancesystem.service.PdfExportService pdfExportService;

        @GetMapping("/reports/daily/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadDailyReportPdf(
                        @RequestParam(required = false) LocalDate date,
                        @RequestParam(required = false) Long departmentId,
                        @RequestParam(required = false) String status)
                        throws java.io.IOException, com.lowagie.text.DocumentException {
                if (date == null)
                        date = LocalDate.now();

                List<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> report = reportService.getDailyReport(date,
                                departmentId, status, org.springframework.data.domain.PageRequest.of(0, 10000))
                                .getContent();
                String deptName = "All Departments";
                if (departmentId != null) {
                        deptName = departmentRepository.findById(departmentId)
                                        .map(root.cyb.mh.attendancesystem.model.Department::getName).orElse("Unknown");
                }

                byte[] pdfBytes = pdfExportService.exportDailyReport(report, date, deptName);

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=daily_report_" + date + "_"
                                                                + System.currentTimeMillis() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/weekly/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadWeeklyReportPdf(
                        @RequestParam(required = false) LocalDate date,
                        @RequestParam(required = false) Long departmentId)
                        throws java.io.IOException, com.lowagie.text.DocumentException {
                if (date == null)
                        date = LocalDate.now();
                LocalDate startOfWeek = date
                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

                List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> report = reportService.getWeeklyReport(
                                startOfWeek,
                                departmentId, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
                String deptName = "All Departments";
                if (departmentId != null) {
                        deptName = departmentRepository.findById(departmentId)
                                        .map(root.cyb.mh.attendancesystem.model.Department::getName).orElse("Unknown");
                }

                byte[] pdfBytes = pdfExportService.exportWeeklyReport(report, startOfWeek, deptName);

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=weekly_report_" + startOfWeek + "_"
                                                                + System.currentTimeMillis() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/monthly/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadMonthlyReportPdf(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Long departmentId)
                        throws java.io.IOException, com.lowagie.text.DocumentException {
                if (year == null || month == null) {
                        LocalDate now = LocalDate.now();
                        year = now.getYear();
                        month = now.getMonthValue();
                }

                List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> report = reportService.getMonthlyReport(year,
                                month,
                                departmentId, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
                String deptName = "All Departments";
                if (departmentId != null) {
                        deptName = departmentRepository.findById(departmentId)
                                        .map(root.cyb.mh.attendancesystem.model.Department::getName).orElse("Unknown");
                }

                byte[] pdfBytes = pdfExportService.exportMonthlyReport(report, year, month, deptName);

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=monthly_report_" + month + "_" + year + "_"
                                                                + System.currentTimeMillis() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/monthly/{employeeId}/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadEmployeeMonthlyReportPdf(
                        @org.springframework.web.bind.annotation.PathVariable String employeeId,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) String period)
                        throws java.io.IOException, com.lowagie.text.DocumentException {

                byte[] pdfBytes;
                String filename;
                LocalDate now = LocalDate.now();

                if (period != null && !period.isEmpty()) {
                        LocalDate startDate;
                        LocalDate endDate = now;
                        if ("3M".equals(period)) {
                                startDate = now.minusMonths(2).withDayOfMonth(1);
                        } else if ("6M".equals(period)) {
                                startDate = now.minusMonths(5).withDayOfMonth(1);
                        } else if ("1Y".equals(period)) {
                                startDate = now.minusMonths(11).withDayOfMonth(1);
                        } else {
                                startDate = now.withDayOfMonth(1);
                        }

                        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeReport = reportService
                                        .getEmployeeRangeReport(employeeId, startDate, endDate);
                        pdfBytes = pdfExportService.exportEmployeeRangeReport(rangeReport);
                        filename = "employee_report_" + employeeId + "_" + period + "_" + System.currentTimeMillis()
                                        + ".pdf";
                } else {
                        if (year == null || month == null) {
                                year = now.getYear();
                                month = now.getMonthValue();
                        }
                        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto report = reportService
                                        .getEmployeeMonthlyReport(employeeId, year, month);
                        pdfBytes = pdfExportService.exportEmployeeMonthlyReport(report);
                        filename = "employee_report_" + employeeId + "_" + month + "_" + year + "_"
                                        + System.currentTimeMillis() + ".pdf";
                }

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=" + filename)
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }
}
