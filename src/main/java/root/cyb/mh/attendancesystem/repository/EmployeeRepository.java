package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.Employee;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, String> {
    java.util.List<Employee> findByDepartmentId(Long departmentId);

    // Check if an employee is a supervisor (either primary or assistant)
    boolean existsByReportsTo_IdOrReportsToAssistant_Id(String primaryId, String assistantId);

    // Find all subordinates for a supervisor (Primary OR Assistant)
    java.util.List<Employee> findByReportsTo_IdOrReportsToAssistant_Id(String primaryId, String assistantId);
}
