package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.AttendanceLog;

import java.time.LocalDateTime;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    boolean existsByEmployeeIdAndTimestampAndDeviceId(String employeeId, LocalDateTime timestamp, Long deviceId);

    java.util.List<AttendanceLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    java.util.List<AttendanceLog> findByEmployeeId(String employeeId);

    @org.springframework.data.jpa.repository.Query("SELECT log FROM AttendanceLog log, Employee emp WHERE log.employeeId = emp.id AND emp.department.id = :departmentId")
    org.springframework.data.domain.Page<AttendanceLog> findByEmployeeDepartmentId(
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
            org.springframework.data.domain.Pageable pageable);
}
