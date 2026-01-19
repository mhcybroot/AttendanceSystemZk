package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.LeaveRequest;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

        // For Employee: View their own history
        List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);

        // For HR/Admin: View all (could filter by Pending later)
        List<LeaveRequest> findAllByOrderByCreatedAtDesc();

        // Find pending requests
        List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequest.Status status);

        // Find requests where the applicant reports to the given approver ID (Primary
        // OR Assistant) - Sorted by Newest First
        List<LeaveRequest> findByEmployee_ReportsTo_IdOrEmployee_ReportsToAssistant_IdOrderByCreatedAtDesc(
                        String approverId,
                        String assistantId);

        // Count approved leaves for a specific date
        long countByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(java.time.LocalDate date1,
                        java.time.LocalDate date2, LeaveRequest.Status status);

        // Find approved leaves for a specific date (for Dashboard filtering)
        List<LeaveRequest> findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(java.time.LocalDate date1,
                        java.time.LocalDate date2, LeaveRequest.Status status);
}
