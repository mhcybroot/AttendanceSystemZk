package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.LeaveRequest;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.LeaveService;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping("/leave")
public class LeaveController {

    @org.springframework.beans.factory.annotation.Value("${app.testing:false}")
    private boolean isTestingMode;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private EmployeeRepository employeeRepository;

    // --- Employee Endpoints ---

    @GetMapping("/employee")
    public String employeeLeavePage(Model model, Principal principal) {
        String employeeId = principal.getName();

        // 1. History
        List<LeaveRequest> history = leaveService.getEmployeeHistory(employeeId);
        model.addAttribute("history", history);
        model.addAttribute("newRequest", new LeaveRequest());
        model.addAttribute("activeLink", "leave");

        return "employee-leave";
    }

    @PostMapping("/employee/apply")
    public String applyForLeave(@ModelAttribute LeaveRequest leaveRequest,
            @RequestParam(name = "proof", required = false) MultipartFile proof,
            Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        if (employee != null) {
            leaveService.createRequest(employee, leaveRequest, proof);
        }
        return "redirect:/leave/employee";
    }

    // --- Admin / HR Endpoints ---

    @GetMapping("/manage")
    public String manageLeavePage(Model model, Authentication authentication) {
        // Fetch User Roles
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN") || r.getAuthority().equals("ROLE_HR"));

        String currentUserId = authentication.getName();
        List<LeaveRequest> requests;

        if (isAdmin) {
            // Admin sees ALL
            requests = leaveService.getAllRequests();
            model.addAttribute("pageTitle", "All Leave Requests");
        } else {
            // Manager sees only THEIR TEAM
            // Strict Security Check: Must be a supervisor
            boolean isSupervisor = employeeRepository.existsByReportsTo_IdOrReportsToAssistant_Id(currentUserId,
                    currentUserId);
            if (!isSupervisor) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access Denied: You are not a supervisor.");
            }

            requests = leaveService.getRequestsForApprover(currentUserId);
            model.addAttribute("pageTitle", "My Team's Requests");
        }

        model.addAttribute("requests", requests);
        model.addAttribute("activeLink", "leave-manage");
        model.addAttribute("isTestingMode", isTestingMode);
        return "admin-leave-requests";
    }

    @PostMapping("/manage/update")
    public String updateStatus(@RequestParam Long id,
            @RequestParam String status,
            @RequestParam(required = false) String comment,
            Authentication authentication) {

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(r -> r.startsWith("ROLE_"))
                .findFirst().orElse("ROLE_USER");

        // Strict Security Check for POST
        boolean isAdmin = role.equals("ROLE_ADMIN") || role.equals("ROLE_HR");
        if (!isAdmin) {
            String currentUserId = authentication.getName();
            boolean isSupervisor = employeeRepository.existsByReportsTo_IdOrReportsToAssistant_Id(currentUserId,
                    currentUserId);
            if (!isSupervisor) {
                return "redirect:/leave/manage?error=AccessDenied";
            }
        }

        String reviewerEmail = authentication.getName();

        try {
            leaveService.updateStatus(id, LeaveRequest.Status.valueOf(status), comment, role, reviewerEmail);
        } catch (IllegalStateException e) {
            // Flash error message
            return "redirect:/leave/manage?error=" + e.getMessage();
        }

        return "redirect:/leave/manage";
    }

    @PostMapping("/manage/delete")
    public String deleteRequest(@RequestParam Long id) {
        // Allow admins to always delete requests (for correction/testing)
        leaveService.deleteRequest(id);
        return "redirect:/leave/manage";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

    @GetMapping("/calendar")
    public String leaveCalendar(Model model) {
        // Fetch Approved Leaves
        List<LeaveRequest> approvedLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(LeaveRequest.Status.APPROVED);

        // Convert to simple JSON for FullCalendar
        StringBuilder jsonEvents = new StringBuilder("[");
        for (int i = 0; i < approvedLeaves.size(); i++) {
            LeaveRequest leave = approvedLeaves.get(i);
            String empName = leave.getEmployee().getName().replace("\"", "'"); // Escape quotes

            jsonEvents.append("{");
            jsonEvents.append("\"title\": \"").append(empName).append(" - ").append(leave.getLeaveType()).append("\",");
            jsonEvents.append("\"start\": \"").append(leave.getStartDate()).append("\",");
            // Add 1 day to end date because FullCalendar end is exclusive
            jsonEvents.append("\"end\": \"").append(leave.getEndDate().plusDays(1)).append("\",");

            // Color coding
            String color = "#0d6efd"; // Default Blue
            if (leave.getLeaveType().equalsIgnoreCase("SICK"))
                color = "#dc3545"; // Red
            else if (leave.getLeaveType().equalsIgnoreCase("CASUAL"))
                color = "#ffc107"; // Yellow/Warning

            jsonEvents.append("\"color\": \"").append(color).append("\"");
            jsonEvents.append("}");

            if (i < approvedLeaves.size() - 1) {
                jsonEvents.append(",");
            }
        }
        jsonEvents.append("]");

        model.addAttribute("calendarEvents", jsonEvents.toString());
        model.addAttribute("activeLink", "leave-calendar");

        return "admin-leave-calendar";
    }
}
