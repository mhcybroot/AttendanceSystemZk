package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.LeaveRequest;
import root.cyb.mh.attendancesystem.repository.LeaveRequestRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LeaveService {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    public LeaveRequest createRequest(Employee employee, LeaveRequest request, MultipartFile file) {
        request.setEmployee(employee);
        request.setStatus(LeaveRequest.Status.PENDING);

        if (file != null && !file.isEmpty()) {
            try {
                String uploadDir = "uploads/leave_proofs/";
                java.io.File directory = new java.io.File(uploadDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path path = Paths.get(uploadDir + fileName);
                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

                request.setProofPath("/uploads/leave_proofs/" + fileName);
            } catch (IOException e) {
                e.printStackTrace(); // Handle error
            }
        }

        return leaveRequestRepository.save(request);
    }

    public List<LeaveRequest> getEmployeeHistory(String employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<LeaveRequest> getAllRequests() {
        return leaveRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<LeaveRequest> getRequestsForApprover(String approverId) {
        // Return requests where user is Primary OR Assistant
        return leaveRequestRepository.findByEmployee_ReportsTo_IdOrEmployee_ReportsToAssistant_IdOrderByCreatedAtDesc(
                approverId,
                approverId);
    }

    public Optional<LeaveRequest> findById(Long id) {
        return leaveRequestRepository.findById(id);
    }

    public void updateStatus(Long requestId, LeaveRequest.Status newStatus, String comment, String reviewerRole,
            String reviewerEmail) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Request ID"));

        // HR Logic: Can only update if PENDING
        if ("ROLE_HR".equals(reviewerRole)) {
            if (request.getStatus() != LeaveRequest.Status.PENDING) {
                throw new IllegalStateException("HR cannot modify a request that is already processed.");
            }
        }

        // Admin Logic: Can override anything (no Check)

        request.setStatus(newStatus);
        request.setAdminComment(comment); // Overwrites previous comment if any
        request.setReviewedBy(reviewerEmail + " (" + reviewerRole + ")");

        leaveRequestRepository.save(request);
    }

    public void deleteRequest(Long id) {
        leaveRequestRepository.deleteById(id);
    }
}
