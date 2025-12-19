package root.cyb.mh.attendancesystem.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;
import root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto;
import root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto;
import root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    // --- Daily Report ---

    public byte[] exportDailyExcel(List<DailyAttendanceDto> report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Daily Report");

            Row headerRow = sheet.createRow(0);
            String[] columns = { "Employee ID", "Name", "Department", "In Time", "Out Time", "Status" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            int rowIdx = 1;
            for (DailyAttendanceDto dto : report) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getEmployeeId());
                row.createCell(1).setCellValue(dto.getEmployeeName());
                row.createCell(2).setCellValue(dto.getDepartmentName());
                row.createCell(3).setCellValue(dto.getInTime() != null ? dto.getInTime().toString() : "-");
                row.createCell(4).setCellValue(dto.getOutTime() != null ? dto.getOutTime().toString() : "-");
                row.createCell(5).setCellValue(dto.getStatus());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportDailyCsv(List<DailyAttendanceDto> report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("Employee ID", "Name", "Department", "In Time", "Out Time", "Status")
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (DailyAttendanceDto dto : report) {
                    printer.printRecord(
                            dto.getEmployeeId(),
                            dto.getEmployeeName(),
                            dto.getDepartmentName(),
                            dto.getInTime() != null ? dto.getInTime().toString() : "-",
                            dto.getOutTime() != null ? dto.getOutTime().toString() : "-",
                            dto.getStatus());
                }
            }
            return out.toByteArray();
        }
    }

    // --- Weekly Report ---

    public byte[] exportWeeklyExcel(List<WeeklyAttendanceDto> report, LocalDate startOfWeek) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Weekly Report");

            Row headerRow = sheet.createRow(0);
            // Dynamic headers for days
            String[] fixedHeaders = { "Employee ID", "Name", "Department" };
            String[] statsHeaders = { "Present", "Absent", "Late", "Early", "Leave" };

            int colIdx = 0;
            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            for (String h : fixedHeaders) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(h);
                cell.setCellStyle(boldStyle);
            }

            for (int i = 0; i < 7; i++) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(startOfWeek.plusDays(i).getDayOfWeek().toString().substring(0, 3));
                cell.setCellStyle(boldStyle);
            }

            for (String h : statsHeaders) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(h);
                cell.setCellStyle(boldStyle);
            }

            int rowIdx = 1;
            for (WeeklyAttendanceDto dto : report) {
                Row row = sheet.createRow(rowIdx++);
                colIdx = 0;
                row.createCell(colIdx++).setCellValue(dto.getEmployeeId());
                row.createCell(colIdx++).setCellValue(dto.getEmployeeName());
                row.createCell(colIdx++).setCellValue(dto.getDepartmentName());

                Map<LocalDate, String> dailyStatus = dto.getDailyStatus();
                for (int i = 0; i < 7; i++) {
                    String status = dailyStatus.getOrDefault(startOfWeek.plusDays(i), "-");
                    // Simplify status for Excel similar to PDF (P, A, etc or full word?) - Full
                    // word is better for Excel
                    // actually let's stick to short codes if it's too long, but Excel has space.
                    // Let's use short codes for readability in grid
                    String code = "-";
                    if (status.contains("PRESENT"))
                        code = "P";
                    else if (status.contains("ABSENT"))
                        code = "A";
                    else if (status.contains("WEEKEND"))
                        code = "W";
                    else if (status.contains("HOLIDAY"))
                        code = "H";
                    else if (status.contains("LATE"))
                        code = "L";
                    else if (status.contains("EARLY"))
                        code = "E";
                    else if (status.contains("LEAVE"))
                        code = "LV";

                    row.createCell(colIdx++).setCellValue(code);
                }

                row.createCell(colIdx++).setCellValue(dto.getPresentCount());
                row.createCell(colIdx++).setCellValue(dto.getAbsentCount());
                row.createCell(colIdx++).setCellValue(dto.getLateCount());
                row.createCell(colIdx++).setCellValue(dto.getEarlyLeaveCount());
                row.createCell(colIdx++).setCellValue(dto.getLeaveCount());
            }

            for (int i = 0; i < colIdx; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportWeeklyCsv(List<WeeklyAttendanceDto> report, LocalDate startOfWeek) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out)) {

            // Build header list
            java.util.List<String> headers = new java.util.ArrayList<>();
            headers.add("Employee ID");
            headers.add("Name");
            headers.add("Department");
            for (int i = 0; i < 7; i++)
                headers.add(startOfWeek.plusDays(i).toString());
            headers.add("Present");
            headers.add("Absent");
            headers.add("Late");
            headers.add("Early");
            headers.add("Leave");

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (WeeklyAttendanceDto dto : report) {
                    java.util.List<Object> record = new java.util.ArrayList<>();
                    record.add(dto.getEmployeeId());
                    record.add(dto.getEmployeeName());
                    record.add(dto.getDepartmentName());

                    Map<LocalDate, String> dailyStatus = dto.getDailyStatus();
                    for (int i = 0; i < 7; i++) {
                        record.add(dailyStatus.getOrDefault(startOfWeek.plusDays(i), "-"));
                    }

                    record.add(dto.getPresentCount());
                    record.add(dto.getAbsentCount());
                    record.add(dto.getLateCount());
                    record.add(dto.getEarlyLeaveCount());
                    record.add(dto.getLeaveCount());

                    printer.printRecord(record);
                }
            }
            return out.toByteArray();
        }
    }

    // --- Monthly Report ---

    public byte[] exportMonthlyExcel(List<MonthlySummaryDto> report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Monthly Report");

            Row headerRow = sheet.createRow(0);
            String[] columns = { "ID", "Name", "Department", "Present", "Absent", "Late", "Early", "Total Leave",
                    "Paid Leave", "Unpaid Leave" };

            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(boldStyle);
            }

            int rowIdx = 1;
            for (MonthlySummaryDto dto : report) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(dto.getEmployeeId());
                row.createCell(col++).setCellValue(dto.getEmployeeName());
                row.createCell(col++).setCellValue(dto.getDepartmentName());
                row.createCell(col++).setCellValue(dto.getPresentCount());
                row.createCell(col++).setCellValue(dto.getAbsentCount());
                row.createCell(col++).setCellValue(dto.getLateCount());
                row.createCell(col++).setCellValue(dto.getEarlyLeaveCount());
                row.createCell(col++).setCellValue(dto.getLeaveCount());
                row.createCell(col++).setCellValue(dto.getPaidLeaveCount());
                row.createCell(col++).setCellValue(dto.getUnpaidLeaveCount());
            }

            for (int i = 0; i < columns.length; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportMonthlyCsv(List<MonthlySummaryDto> report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("ID", "Name", "Department", "Present", "Absent", "Late", "Early", "Total Leave",
                            "Paid Leave", "Unpaid Leave")
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (MonthlySummaryDto dto : report) {
                    printer.printRecord(
                            dto.getEmployeeId(),
                            dto.getEmployeeName(),
                            dto.getDepartmentName(),
                            dto.getPresentCount(),
                            dto.getAbsentCount(),
                            dto.getLateCount(),
                            dto.getEarlyLeaveCount(),
                            dto.getLeaveCount(),
                            dto.getPaidLeaveCount(),
                            dto.getUnpaidLeaveCount());
                }
            }
            return out.toByteArray();
        }
    }

    // --- Employee Detail (No Range for now, simple monthly detail) ---
    // Can expand if needed
}
