package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.LeaveRequest;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.LeaveService;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveService leaveService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        String currentUserId = authentication.getName();

        // 1. Security Check
        boolean isSupervisor = employeeRepository.existsByReportsTo_IdOrReportsToAssistant_Id(currentUserId,
                currentUserId);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));

        if (!isSupervisor && !isAdmin) {
            throw new AccessDeniedException("Access Denied: You are not a supervisor.");
        }

        // 2. Fetch Team Members
        List<Employee> teamMembers = employeeRepository.findByReportsTo_IdOrReportsToAssistant_Id(currentUserId,
                currentUserId);
        model.addAttribute("teamMembers", teamMembers);

        // 3. Fetch Pending Requests
        List<LeaveRequest> allRequests = leaveService.getRequestsForApprover(currentUserId);
        List<LeaveRequest> pendingRequests = allRequests.stream()
                .filter(req -> req.getStatus() == LeaveRequest.Status.PENDING)
                .collect(Collectors.toList());

        // Recent 5 requests
        List<LeaveRequest> recentRequests = allRequests.stream().limit(5).collect(Collectors.toList());

        model.addAttribute("pendingCount", pendingRequests.size());
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("recentRequests", recentRequests);
        model.addAttribute("teamSize", teamMembers.size());
        model.addAttribute("activeLink", "supervisor-dashboard");

        return "supervisor-dashboard";
    }
}
