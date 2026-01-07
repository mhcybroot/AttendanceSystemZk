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
import root.cyb.mh.attendancesystem.service.ReportService;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

        @Autowired
        private EmployeeRepository employeeRepository;

        @Autowired
        private LeaveService leaveService;

        @Autowired
        private ReportService reportService;

        @Autowired
        private root.cyb.mh.attendancesystem.service.DashboardService dashboardService;

        @GetMapping("/employee/{id}")
        public String viewEmployeeDashboard(@org.springframework.web.bind.annotation.PathVariable String id,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer year,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer month,
                        Model model, Authentication authentication) {
                String currentUserId = authentication.getName();

                // 1. Security Check: Ensure Supervisor manages this employee
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Invalid Employee ID"));

                boolean isSupervisor = (employee.getReportsTo() != null
                                && employee.getReportsTo().getId().equals(currentUserId));
                boolean isAssistant = (employee.getReportsToAssistant() != null
                                && employee.getReportsToAssistant().getId().equals(currentUserId));
                boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                                || a.getAuthority().equals("ROLE_HR"));

                if (!isSupervisor && !isAssistant && !isAdmin) {
                        throw new AccessDeniedException("Access Denied: You cannot view this employee's dashboard.");
                }

                // Defaults
                if (year == null)
                        year = java.time.LocalDate.now().getYear();
                if (month == null)
                        month = java.time.LocalDate.now().getMonthValue();

                // 2. Fetch Dashboard Data
                java.util.Map<String, Object> data = dashboardService.getDashboardData(id, year, month);
                model.addAllAttributes(data);

                // 3. Set Context for View
                model.addAttribute("activeLink", "team-dashboard");
                model.addAttribute("selectedYear", year);
                model.addAttribute("selectedMonth", month);

                return "supervisor-employee-details";
        }

        @GetMapping("/dashboard")
        public String dashboard(Model model, Authentication authentication) {
                String currentUserId = authentication.getName();

                // 1. Security Check
                boolean isSupervisor = employeeRepository.existsByReportsTo_IdOrReportsToAssistant_Id(currentUserId,
                                currentUserId);
                boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                                || a.getAuthority().equals("ROLE_HR"));

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
                model.addAttribute("recentRequests", recentRequests);
                model.addAttribute("teamSize", teamMembers.size());

                // 4. Team Pulse (Real-time Status)
                List<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> teamPulse = reportService
                                .getTeamDailyStatus(teamMembers);
                model.addAttribute("teamPulse", teamPulse);

                // Calculate Quick Stats for Dashboard
                long presentCount = teamPulse.stream()
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE"))
                                .count();
                model.addAttribute("presentCount", presentCount);

                // 5. Team Analytics (This Month)
                java.time.LocalDate now = java.time.LocalDate.now();
                List<Integer> monthList = java.util.Collections.singletonList(now.getMonthValue());
                List<Long> teamUserIds = teamMembers.stream().map(e -> Long.parseLong(e.getId()))
                                .collect(Collectors.toList());
                // Note: ReportService expects Department IDs or we can filter manually.
                // Let's reuse getTeamDailyStatus logic but over a date range?
                // Actually ReportService.getMonthlyReport is optimized for Depts.
                // For a specific list of employees, it's better to fetch logs directly or add a
                // method.
                // Simpler approach: Calculate strictly from daily pulse? No, that's just today.
                // Let's use ReportService.getMonthlyReport but filter the result list for our
                // team.

                List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> monthlyStats = reportService.getMonthlyReport(
                                now.getYear(), monthList, null, org.springframework.data.domain.PageRequest.of(0, 1000))
                                .getContent();

                // Filter for Team Members only
                List<String> teamIds = teamMembers.stream().map(Employee::getId).collect(Collectors.toList());
                List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> teamStats = monthlyStats.stream()
                                .filter(dto -> teamIds.contains(dto.getEmployeeId()))
                                .collect(Collectors.toList());

                // Aggregate
                long totalPresent = teamStats.stream()
                                .mapToLong(root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getPresentCount).sum();
                long totalLate = teamStats.stream()
                                .mapToLong(root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getLateCount).sum();
                long totalOnTime = totalPresent - totalLate; // Approximation as Present includes Late
                // Wait, typically Present includes Late. Let's check DTO.
                // Yes, ReportService: present++ (includes late), then late++.
                // So OnTime = Present - Late.

                double onTimeRate = totalPresent > 0 ? (double) totalOnTime / totalPresent * 100 : 0;
                model.addAttribute("onTimeRate", (int) onTimeRate);
                model.addAttribute("monthlyPresent", totalPresent);
                model.addAttribute("monthlyLate", totalLate);

                // 6. Fetch Pending Advance Salary Requests
                List<root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest> advanceRequests = advanceSalaryRepository
                                .findByEmployee_ReportsTo_IdOrEmployee_ReportsToAssistant_Id(currentUserId,
                                                currentUserId);
                List<root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest> pendingAdvanceRequests = advanceRequests
                                .stream()
                                .filter(req -> req
                                                .getStatus() == root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest.Status.PENDING)
                                .collect(Collectors.toList());

                List<root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest> historyAdvanceRequests = advanceRequests
                                .stream()
                                .filter(req -> req
                                                .getStatus() != root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest.Status.PENDING)
                                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                                .collect(Collectors.toList());

                model.addAttribute("pendingAdvanceRequests", pendingAdvanceRequests);
                model.addAttribute("historyAdvanceRequests", historyAdvanceRequests);

                model.addAttribute("activeLink", "supervisor-dashboard");
                return "supervisor-dashboard";
        }

        @GetMapping("/advance/history")
        public String viewAdvanceHistory(Model model, Authentication authentication) {
                String currentUserId = authentication.getName();

                // 1. Security Check (ensure supervisor)
                boolean isSupervisor = employeeRepository.existsByReportsTo_IdOrReportsToAssistant_Id(currentUserId,
                                currentUserId);
                boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                                || a.getAuthority().equals("ROLE_HR"));

                if (!isSupervisor && !isAdmin) {
                        throw new AccessDeniedException("Access Denied");
                }

                // 2. Fetch All Requests
                List<root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest> advanceRequests = advanceSalaryRepository
                                .findByEmployee_ReportsTo_IdOrEmployee_ReportsToAssistant_Id(currentUserId,
                                                currentUserId);

                // 3. Filter for History (Not Pending)
                List<root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest> historyAdvanceRequests = advanceRequests
                                .stream()
                                .filter(req -> req
                                                .getStatus() != root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest.Status.PENDING)
                                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                                .collect(Collectors.toList());

                model.addAttribute("historyAdvanceRequests", historyAdvanceRequests);
                return "supervisor-advance-history";
        }

        @org.springframework.web.bind.annotation.PostMapping("/advance/approve")
        public String approveAdvance(@org.springframework.web.bind.annotation.RequestParam Long id,
                        Authentication authentication) {
                processAdvance(id, root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest.Status.APPROVED,
                                "Approved by Supervisor", authentication);
                return "redirect:/supervisor/dashboard";
        }

        @org.springframework.web.bind.annotation.PostMapping("/advance/reject")
        public String rejectAdvance(@org.springframework.web.bind.annotation.RequestParam Long id,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String comment,
                        Authentication authentication) {
                processAdvance(id, root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest.Status.REJECTED, comment,
                                authentication);
                return "redirect:/supervisor/dashboard";
        }

        private void processAdvance(Long id, root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest.Status status,
                        String comment, Authentication authentication) {
                String currentUserId = authentication.getName();

                root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest req = advanceSalaryRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Invalid ID"));

                // Security: Ensure the approver is actually the supervisor
                boolean isSupervisor = req.getEmployee().getReportsTo() != null
                                && req.getEmployee().getReportsTo().getId().equals(currentUserId);
                boolean isAssistant = req.getEmployee().getReportsToAssistant() != null
                                && req.getEmployee().getReportsToAssistant().getId().equals(currentUserId);

                if (!isSupervisor && !isAssistant) {
                        throw new AccessDeniedException("You are not authorized to process this request.");
                }

                req.setStatus(status);
                req.setAdminComment(comment); // Using existing field
                advanceSalaryRepository.save(req);
        }

        @Autowired
        private root.cyb.mh.attendancesystem.repository.AdvanceSalaryRepository advanceSalaryRepository;
}
