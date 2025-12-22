package root.cyb.mh.attendancesystem.dto;

import lombok.Data;
import root.cyb.mh.attendancesystem.model.Payslip;

@Data
public class PayrollMonthlySummaryDto {
    private String month;
    private int totalEmployees;
    private int draftCount;
    private int paidCount;
    private double totalNetSalary;

    public PayrollMonthlySummaryDto(String month, java.util.List<Payslip> slips) {
        this.month = month;
        this.totalEmployees = slips.size();
        this.draftCount = (int) slips.stream().filter(s -> s.getStatus() == Payslip.Status.DRAFT).count();
        this.paidCount = (int) slips.stream().filter(s -> s.getStatus() == Payslip.Status.PAID).count();
        this.totalNetSalary = slips.stream().mapToDouble(Payslip::getNetSalary).sum();
    }
}
