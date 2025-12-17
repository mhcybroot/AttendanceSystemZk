package root.cyb.mh.attendancesystem.dto;

import java.time.LocalDate;
import java.util.List;

public class EmployeeMonthlyDetailDto {
    private String employeeName;
    private String employeeId;
    private String departmentName;
    private int year;
    private int month;
    private List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> dailyDetails; // Reusing
                                                                                                     // DailyDetail from
                                                                                                     // Weekly DTO

    private int totalPresent;
    private int totalAbsent;
    private int totalLates;
    private int totalEarlyLeaves;
    private int totalLeaves;
    private int paidLeavesCount; // Renamed to slightly distinct from monthly summary if needed, but consistent
                                 // is better. Let's use same name style.
    private int unpaidLeavesCount;

    // Getters and Setters
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

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> getDailyDetails() {
        return dailyDetails;
    }

    public void setDailyDetails(
            List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> dailyDetails) {
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

    public int getPaidLeavesCount() {
        return paidLeavesCount;
    }

    public void setPaidLeavesCount(int paidLeavesCount) {
        this.paidLeavesCount = paidLeavesCount;
    }

    public int getUnpaidLeavesCount() {
        return unpaidLeavesCount;
    }

    public void setUnpaidLeavesCount(int unpaidLeavesCount) {
        this.unpaidLeavesCount = unpaidLeavesCount;
    }
}
