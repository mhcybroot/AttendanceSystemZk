package root.cyb.mh.attendancesystem.dto;

public class MonthlySummaryDto {
    private String employeeId;
    private String employeeName;
    private String departmentName;

    private int presentCount;
    private int absentCount;
    private int lateCount;
    private int earlyLeaveCount;
    private int leaveCount;
    private int paidLeaveCount;
    private int unpaidLeaveCount;

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public void setPresentCount(int presentCount) {
        this.presentCount = presentCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    public int getLateCount() {
        return lateCount;
    }

    public void setLateCount(int lateCount) {
        this.lateCount = lateCount;
    }

    public int getEarlyLeaveCount() {
        return earlyLeaveCount;
    }

    public void setEarlyLeaveCount(int earlyLeaveCount) {
        this.earlyLeaveCount = earlyLeaveCount;
    }

    public int getLeaveCount() {
        return leaveCount;
    }

    public void setLeaveCount(int leaveCount) {
        this.leaveCount = leaveCount;
    }

    public int getPaidLeaveCount() {
        return paidLeaveCount;
    }

    public void setPaidLeaveCount(int paidLeaveCount) {
        this.paidLeaveCount = paidLeaveCount;
    }

    public int getUnpaidLeaveCount() {
        return unpaidLeaveCount;
    }

    public void setUnpaidLeaveCount(int unpaidLeaveCount) {
        this.unpaidLeaveCount = unpaidLeaveCount;
    }

    private int month;
    private int year;

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}
