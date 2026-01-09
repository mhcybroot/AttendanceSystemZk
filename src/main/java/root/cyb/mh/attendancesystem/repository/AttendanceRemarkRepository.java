package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.AttendanceRemark;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRemarkRepository extends JpaRepository<AttendanceRemark, Long> {
    List<AttendanceRemark> findByEmployeeId(String employeeId);

    Optional<AttendanceRemark> findByEmployeeIdAndDate(String employeeId, LocalDate date);

    List<AttendanceRemark> findByEmployeeIdAndDateBetween(String employeeId, LocalDate startDate, LocalDate endDate);
}
