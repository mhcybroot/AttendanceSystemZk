package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import root.cyb.mh.attendancesystem.model.Payslip;
import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByMonth(String month);

    List<Payslip> findByEmployeeIdOrderByMonthDesc(String employeeId);

    Optional<Payslip> findByEmployeeIdAndMonth(String employeeId, String month);
}
