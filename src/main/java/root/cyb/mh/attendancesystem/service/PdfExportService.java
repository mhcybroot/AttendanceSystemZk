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

    @Value("${app.company.name}")
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

    public byte[] exportMonthlyReport(List<MonthlySummaryDto> report, int year, int month, String departmentName)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, "Monthly Attendance Summary",
                    "Period: " + month + "/" + year,
                    departmentName);

            PdfPTable table = new PdfPTable(8); // ID, Name, Dept, Present, Absent, Late, Early, Leave
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2, 4, 3, 2, 2, 2, 2, 2 });

            addTableHeader(table, "ID", "Name", "Department", "Present", "Absent", "Late", "Early", "Leave");

            for (MonthlySummaryDto dto : report) {
                addCell(table, dto.getEmployeeId());
                addCell(table, dto.getEmployeeName());
                addCell(table, dto.getDepartmentName());
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

    public byte[] exportEmployeeMonthlyReport(EmployeeMonthlyDetailDto report) throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, "Individual Monthly Attendance Report",
                    "Employee: " + report.getEmployeeName() + " (" + report.getEmployeeId() + ")",
                    "Department: " + report.getDepartmentName() + " | Period: " + report.getMonth() + "/"
                            + report.getYear());

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
                addCell(table,
                        day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min" : "-");
                addCell(table, day.getStatus());
            }

            document.add(table);

            // Summary
            Paragraph summary = new Paragraph("\nSummary: Present: " + report.getTotalPresent() +
                    " | Absent: " + report.getTotalAbsent() +
                    " | Late: " + report.getTotalLates() +
                    " | Early: " + report.getTotalEarlyLeaves() +
                    " | Leave: " + report.getTotalLeaves(), HEADER_FONT);
            document.add(summary);

            addFooter(document);
            document.close();
            return out.toByteArray();
        }
    }

    // --- Helper Methods ---

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
