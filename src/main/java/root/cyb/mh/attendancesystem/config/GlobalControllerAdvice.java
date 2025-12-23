package root.cyb.mh.attendancesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Value("${app.company.name:Attendance System}")
    private String companyName;

    @ModelAttribute("companyName")
    public String companyName() {
        return companyName;
    }

    @ModelAttribute("isSupervisor")
    public boolean isSupervisor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal().equals("anonymousUser")) {
            return false;
        }
        String currentUserId = authentication.getName();
        // Check if anyone reports to this user (Primary or Assistant)
        return employeeRepository.existsByReportsTo_IdOrReportsToAssistant_Id(currentUserId, currentUserId);
    }
}
