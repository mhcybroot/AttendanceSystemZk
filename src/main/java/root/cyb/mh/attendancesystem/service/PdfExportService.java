package root.cyb.mh.attendancesystem.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;
import root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {

    @Value("${app.company.name:Skylink Innovation Limited}")
    private String companyName;

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font DATA_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8);

    public byte[] exportDailyReport(List<DailyAttendanceDto> report, LocalDate date, String departmentName)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, "Daily Attendance Report", "Date: " + date, departmentName);

            PdfPTable table = new PdfPTable(6); // Emp, Name, Dept, In, Out, Status
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2, 4, 3, 2, 2, 3 });

            addTableHeader(table, "ID", "Name", "Department", "In Time", "Out Time", "Status");

            for (DailyAttendanceDto dto : report) {
                addCell(table, dto.getEmployeeId());
                addCell(table, dto.getEmployeeName());
                addCell(table, dto.getDepartmentName());
                addCell(table, dto.getInTime() != null ? dto.getInTime().toString() : "-");
                addCell(table, dto.getOutTime() != null ? dto.getOutTime().toString() : "-");
                addCell(table, dto.getStatus());
            }

            document.add(table);
            addFooter(document);
            document.close();
            return out.toByteArray();
        }
    }

    public byte[] exportWeeklyReport(List<WeeklyAttendanceDto> report, LocalDate startOfWeek, String departmentName)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate()); // Landscape
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, "Weekly Attendance Report",
                    "Week of: " + startOfWeek + " to " + startOfWeek.plusDays(6),
                    departmentName);

            // Re-calc widths array size properly
            // 3 + 7 + 5 (P, A, L, E, LV) = 15 columns
            PdfPTable table = new PdfPTable(15);
            table.setWidthPercentage(100);
            float[] widths = new float[15];
            widths[0] = 1.5f; // ID
            widths[1] = 3f; // Name
            widths[2] = 2f; // Dept
            for (int i = 3; i < 10; i++)
                widths[i] = 1.2f; // Days
            widths[10] = 1f; // P
            widths[11] = 1f; // A
            widths[12] = 1f; // L
            widths[13] = 1f; // E
            widths[14] = 1f; // LV

            table.setWidths(widths);

            // Header Row
            addTableHeader(table, "ID", "Name", "Dept");
            LocalDate current = startOfWeek;
            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE dd");
            for (int i = 0; i < 7; i++) {
                addTableHeader(table, current.plusDays(i).format(dayFmt));
            }
            addTableHeader(table, "P", "A", "L", "E", "LV");

            for (WeeklyAttendanceDto dto : report) {
                addCell(table, dto.getEmployeeId());
                addCell(table, dto.getEmployeeName());
                addCell(table, dto.getDepartmentName());

                Map<LocalDate, String> statusMap = dto.getDailyStatus();
                LocalDate day = startOfWeek;
                for (int i = 0; i < 7; i++) {
                    String status = statusMap.getOrDefault(day, "-");
                    // Abbreviate
                    if (status.contains("PRESENT"))
                        status = "P";
                    else if (status.contains("ABSENT"))
                        status = "A";
                    else if (status.contains("WEEKEND"))
                        status = "W";
                    else if (status.contains("HOLIDAY"))
                        status = "H";
                    else if (status.contains("LATE"))
                        status = "L";
                    else if (status.contains("EARLY"))
                        status = "E";
                    else if (status.contains("LEAVE"))
                        status = "LV";
                    else if (status.contains("LEAVE"))
                        status = "LV";

                    addCell(table, status);
                    day = day.plusDays(1);
                }

                addCell(table, String.valueOf(dto.getPresentCount()));
                addCell(table, String.valueOf(dto.getAbsentCount()));
                addCell(table, String.valueOf(dto.getLateCount()));
                addCell(table, String.valueOf(dto.getEarlyLeaveCount()));
                addCell(table, String.valueOf(dto.getLeaveCount()));
            }

            document.add(table);
            addFooter(document);
            document.close();
            return out.toByteArray();
        }

    }

    public byte[] exportMonthlyReport(List<MonthlySummaryDto> report, int year, List<Integer> months,
            String departmentName)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
            PdfWriter.getInstance(document, out);
            document.open();

            // Format months for header
            String monthNames = months.stream()
                    .map(m -> java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT,
                            java.util.Locale.ENGLISH))
                    .collect(java.util.stream.Collectors.joining(", "));

            addHeader(document, "Monthly Attendance Summary",
                    "Period: " + monthNames + " " + year,
                    departmentName);

            // Updated width for 11 columns (Added Period)
            // ID, Name, Dept, Period, Pres, Abs, Late, Early, Total LV, Paid LV, Unpaid LV
            PdfPTable table = new PdfPTable(11);
            table.setWidthPercentage(100);

            // Adjust widths:
            // ID(1.5), Name(3), Dept(2), Period(1.5), P(1), A(1), L(1), E(1), TL(1), PL(1),
            // UL(1) -> Sum ~15
            table.setWidths(new float[] { 1.5f, 3f, 2f, 1.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f });

            addTableHeader(table, "ID", "Name", "Dept", "Period", "Pres", "Abs", "Late", "Early", "Total LV", "Paid LV",
                    "Unpaid LV");

            for (MonthlySummaryDto dto : report) {
                addCell(table, dto.getEmployeeId());
                addCell(table, dto.getEmployeeName());
                addCell(table, dto.getDepartmentName());
                // Add Period Cell
                String period = java.time.Month.of(dto.getMonth()).getDisplayName(java.time.format.TextStyle.SHORT,
                        java.util.Locale.ENGLISH)
                        + "-" + dto.getYear();
                addCell(table, period);

                addCell(table, String.valueOf(dto.getPresentCount()));
                addCell(table, String.valueOf(dto.getAbsentCount()));
                addCell(table, String.valueOf(dto.getLateCount()));
                addCell(table, String.valueOf(dto.getEarlyLeaveCount()));
                addCell(table, String.valueOf(dto.getLeaveCount()));
                addCell(table, String.valueOf(dto.getPaidLeaveCount()));
                addCell(table, String.valueOf(dto.getUnpaidLeaveCount()));
            }

            document.add(table);
            addFooter(document);
            document.close();
            return out.toByteArray();
        }
    }

    public byte[] exportEmployeeMonthlyReport(EmployeeMonthlyDetailDto report) throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            addMonthlyReportContent(document, report);

            addFooter(document);
            document.close();
            return out.toByteArray();
        }
    }

    public byte[] exportEmployeeRangeReport(root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeReport)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // Main Header
            addHeader(document, "Attendance History Report",
                    "Employee: " + rangeReport.getEmployeeName() + " (" + rangeReport.getEmployeeId() + ")",
                    "Period: " + rangeReport.getStartDate() + " to " + rangeReport.getEndDate());

            // Overall Summary
            Paragraph summary = new Paragraph("Period Summary: Present: " + rangeReport.getTotalPresent() +
                    " | Absent: " + rangeReport.getTotalAbsent() +
                    " | Late: " + rangeReport.getTotalLates() +
                    "\nTotal Leaves: " + rangeReport.getTotalLeaves() +
                    " (Paid: " + rangeReport.getTotalPaidLeaves() + ", Unpaid: " + rangeReport.getTotalUnpaidLeaves()
                    + ")\n\n",
                    HEADER_FONT);
            document.add(summary);

            // Loop through months
            for (EmployeeMonthlyDetailDto monthArg : rangeReport.getMonthlyReports()) {
                document.add(new Paragraph("\n"));
                // Sub-section Header
                Paragraph monthTitle = new Paragraph(java.time.Month.of(monthArg.getMonth()) + " " + monthArg.getYear(),
                        HEADER_FONT);
                monthTitle.setAlignment(Element.ALIGN_LEFT);
                document.add(monthTitle);
                document.add(new Paragraph("\n"));

                // Add table
                addMonthlyTable(document, monthArg);

                // Monthly Summary line
                Paragraph mSummary = new Paragraph("Month Summary: P: " + monthArg.getTotalPresent() +
                        " | A: " + monthArg.getTotalAbsent() +
                        " | Paid Lv: " + monthArg.getPaidLeavesCount() +
                        " | Unpaid Lv: " + monthArg.getUnpaidLeavesCount(), SMALL_FONT);
                document.add(mSummary);
                document.add(new Paragraph("----------------------------------------------------------------"));
            }

            addFooter(document);
            document.close();
            return out.toByteArray();
        }
    }

    private void addMonthlyReportContent(Document document, EmployeeMonthlyDetailDto report) throws DocumentException {
        addHeader(document, "Individual Monthly Attendance Report",
                "Employee: " + report.getEmployeeName() + " (" + report.getEmployeeId() + ")",
                "Department: " + report.getDepartmentName() + " | Period: " + report.getMonth() + "/"
                        + report.getYear());

        addMonthlyTable(document, report);

        // Summary
        Paragraph summary = new Paragraph("\nSummary: Present: " + report.getTotalPresent() +
                " | Absent: " + report.getTotalAbsent() +
                " | Late: " + report.getTotalLates() +
                " | Early: " + report.getTotalEarlyLeaves() +
                "\nTotal Leaves: " + report.getTotalLeaves() +
                " (Paid: " + report.getPaidLeavesCount() + ", Unpaid: " + report.getUnpaidLeavesCount() + ")",
                HEADER_FONT);
        document.add(summary);
    }

    private void addMonthlyTable(Document document, EmployeeMonthlyDetailDto report) throws DocumentException {
        PdfPTable table = new PdfPTable(7); // Date, Day, In, Out, Late, Early, Status
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 2, 2, 2, 2, 2, 2, 3 });

        addTableHeader(table, "Date", "Day", "In Time", "Out Time", "Late", "Early", "Status");

        for (EmployeeWeeklyDetailDto.DailyDetail day : report.getDailyDetails()) {
            addCell(table, day.getDate().toString());
            addCell(table, day.getDayOfWeek());
            addCell(table, day.getInTime() != null ? day.getInTime().toString() : "-");
            addCell(table, day.getOutTime() != null ? day.getOutTime().toString() : "-");
            addCell(table, day.getLateDurationMinutes() > 0 ? day.getLateDurationMinutes() + " min" : "-");
            addCell(table, day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min" : "-");
            addCell(table, day.getStatus());
        }

        document.add(table);
    }

    public byte[] exportPayslipPdf(root.cyb.mh.attendancesystem.model.Payslip payslip)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header
            Paragraph compName = new Paragraph(companyName, TITLE_FONT);
            compName.setAlignment(Element.ALIGN_CENTER);
            document.add(compName);

            Paragraph title = new Paragraph("OFFICIAL PAYSLIP", HEADER_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // 2. Employee Info Table
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[] { 1, 1 });

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Phrase("Employee: " + payslip.getEmployee().getName(), HEADER_FONT));
            leftCell.addElement(new Phrase("ID: " + payslip.getEmployee().getId(), DATA_FONT));
            leftCell.addElement(new Phrase("Department: "
                    + (payslip.getEmployee().getDepartment() != null ? payslip.getEmployee().getDepartment().getName()
                            : "-"),
                    DATA_FONT));
            infoTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(new Paragraph("Pay Period: " + payslip.getMonth(), HEADER_FONT)); // YYYY-MM
            rightCell.addElement(new Paragraph("Generated: " + payslip.getGeneratedAt().toLocalDate(), DATA_FONT));
            infoTable.addCell(rightCell);

            document.add(infoTable);
            document.add(new Paragraph("\n"));

            // 3. Attendance Summary
            PdfPTable attTable = new PdfPTable(4);
            attTable.setWidthPercentage(100);
            addTableHeader(attTable, "Working Days", "Present", "Absent", "Late/Leaves");

            addCell(attTable, String.valueOf(payslip.getTotalWorkingDays()));
            addCell(attTable, String.valueOf(payslip.getPresentDays()));
            addCell(attTable, String.valueOf(payslip.getAbsentDays()));
            addCell(attTable, "L: " + payslip.getLateDays() + ", LV: " + payslip.getPaidLeaveDays());

            document.add(attTable);
            document.add(new Paragraph("\n"));

            // 4. Financials
            PdfPTable finTable = new PdfPTable(2);
            finTable.setWidthPercentage(100);
            finTable.setWidths(new float[] { 3, 1 }); // Desc, Amount

            // Header
            PdfPCell h1 = new PdfPCell(new Phrase("Description", HEADER_FONT));
            h1.setBackgroundColor(Color.LIGHT_GRAY);
            finTable.addCell(h1);
            PdfPCell h2 = new PdfPCell(new Phrase("Amount (BDT)", HEADER_FONT));
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setHorizontalAlignment(Element.ALIGN_RIGHT);
            finTable.addCell(h2);

            // Rows
            addFinRow(finTable, "Basic Salary", payslip.getBasicSalary());

            if (payslip.getAllowanceAmount() != null && payslip.getAllowanceAmount() > 0) {
                addFinRow(finTable, "Fixed Allowance", payslip.getAllowanceAmount());
            }

            if (payslip.getBonusAmount() != null && payslip.getBonusAmount() > 0) {
                addFinRow(finTable, "Bonus / Overtime", payslip.getBonusAmount());
            }

            if (payslip.getDeductionAmount() != null && payslip.getDeductionAmount() > 0) {
                // Show as negative
                addFinRow(finTable, "Deductions (Absence/Penalties)", -payslip.getDeductionAmount());
            }

            if (payslip.getLatePenaltyAmount() != null && payslip.getLatePenaltyAmount() > 0) {
                addFinRow(finTable, "Late Penalty", -payslip.getLatePenaltyAmount());
            }

            // Net Pay
            PdfPCell netLabel = new PdfPCell(new Phrase("NET PAYABLE", HEADER_FONT));
            netLabel.setPadding(5);
            finTable.addCell(netLabel);

            PdfPCell netVal = new PdfPCell(new Phrase(String.format("%.2f", payslip.getNetSalary()), HEADER_FONT));
            netVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            netVal.setPadding(5);
            finTable.addCell(netVal);

            document.add(finTable);

            // 5. Footer
            Paragraph footer = new Paragraph(
                    "\n\n\n__________________________                  __________________________\nEmployee Signature                                     Authorized Signature",
                    SMALL_FONT);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        }
    }

    private void addFinRow(PdfPTable table, String desc, Double amount) {
        PdfPCell c1 = new PdfPCell(new Phrase(desc, DATA_FONT));
        c1.setPadding(5);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(String.format("%.2f", amount != null ? amount : 0.0), DATA_FONT));
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(5);
        table.addCell(c2);
    }

    private void addHeader(Document document, String reportTitle, String subTitle, String department)
            throws DocumentException {
        Paragraph company = new Paragraph(companyName, TITLE_FONT);
        company.setAlignment(Element.ALIGN_CENTER);
        document.add(company);

        Paragraph title = new Paragraph(reportTitle, HEADER_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph sub = new Paragraph(subTitle, DATA_FONT);
        sub.setAlignment(Element.ALIGN_CENTER);
        document.add(sub);

        if (department != null && !department.isEmpty()) {
            Paragraph dept = new Paragraph("Department: " + department, DATA_FONT);
            dept.setAlignment(Element.ALIGN_CENTER);
            document.add(dept);
        }

        document.add(new Paragraph("\n")); // Space
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "\n\nGenerated on: "
                        + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                SMALL_FONT);
        footer.setAlignment(Element.ALIGN_RIGHT);
        document.add(footer);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", DATA_FONT));
        cell.setPadding(5);
        table.addCell(cell);
    }
}
