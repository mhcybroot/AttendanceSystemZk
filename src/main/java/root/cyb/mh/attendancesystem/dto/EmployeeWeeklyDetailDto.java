package root.cyb.mh.attendancesystem.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class EmployeeWeeklyDetailDto {
    private String employeeName;
    private String employeeId;
    private String departmentName;
    private LocalDate startOfWeek;
    private LocalDate endOfWeek;
    private List<DailyDetail> dailyDetails;

    private int totalPresent;
    private int totalAbsent;
    private int totalLates;
    private int totalEarlyLeaves;
    private int totalLeaves;

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public LocalDate getStartOfWeek() {
        return startOfWeek;
    }

    public void setStartOfWeek(LocalDate startOfWeek) {
        this.startOfWeek = startOfWeek;
    }

    public LocalDate getEndOfWeek() {
        return endOfWeek;
    }

    public void setEndOfWeek(LocalDate endOfWeek) {
        this.endOfWeek = endOfWeek;
    }

    public List<DailyDetail> getDailyDetails() {
        return dailyDetails;
    }

    public void setDailyDetails(List<DailyDetail> dailyDetails) {
        this.dailyDetails = dailyDetails;
    }

    public int getTotalPresent() {
        return totalPresent;
    }

    public void setTotalPresent(int totalPresent) {
        this.totalPresent = totalPresent;
    }

    public int getTotalAbsent() {
        return totalAbsent;
    }

    public void setTotalAbsent(int totalAbsent) {
        this.totalAbsent = totalAbsent;
    }

    public int getTotalLates() {
        return totalLates;
    }

    public void setTotalLates(int totalLates) {
        this.totalLates = totalLates;
    }

    public int getTotalEarlyLeaves() {
        return totalEarlyLeaves;
    }

    public void setTotalEarlyLeaves(int totalEarlyLeaves) {
        this.totalEarlyLeaves = totalEarlyLeaves;
    }

    public int getTotalLeaves() {
        return totalLeaves;
    }

    public void setTotalLeaves(int totalLeaves) {
        this.totalLeaves = totalLeaves;
    }

    public static class DailyDetail {
        private LocalDate date;
        private String dayOfWeek;
        private String status;
        private String statusColor;
        private LocalTime inTime;
        private LocalTime outTime;
        private long lateDurationMinutes;
        private long earlyLeaveDurationMinutes;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getDayOfWeek() {
            return dayOfWeek;
        }

        public void setDayOfWeek(String dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatusColor() {
            return statusColor;
        }

        public void setStatusColor(String statusColor) {
            this.statusColor = statusColor;
        }

        public LocalTime getInTime() {
            return inTime;
        }

        public void setInTime(LocalTime inTime) {
            this.inTime = inTime;
        }

        public LocalTime getOutTime() {
            return outTime;
        }

        public void setOutTime(LocalTime outTime) {
            this.outTime = outTime;
        }

        public long getLateDurationMinutes() {
            return lateDurationMinutes;
        }

        public void setLateDurationMinutes(long lateDurationMinutes) {
            this.lateDurationMinutes = lateDurationMinutes;
        }

        public long getEarlyLeaveDurationMinutes() {
            return earlyLeaveDurationMinutes;
        }

        public void setEarlyLeaveDurationMinutes(long earlyLeaveDurationMinutes) {
            this.earlyLeaveDurationMinutes = earlyLeaveDurationMinutes;
        }

        private String lateNote;

        public String getLateNote() {
            return lateNote;
        }

        public void setLateNote(String lateNote) {
            this.lateNote = lateNote;
        }
    }
}
