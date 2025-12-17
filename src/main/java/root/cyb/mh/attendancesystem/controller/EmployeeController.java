package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String listEmployees(Model model,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir) {

        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("asc")
                ? org.springframework.data.domain.Sort.by(sortField).ascending()
                : org.springframework.data.domain.Sort.by(sortField).descending();

        model.addAttribute("employees", employeeRepository.findAll(sort));
        model.addAttribute("newEmployee", new Employee());
        model.addAttribute("departments", departmentRepository.findAll());

        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "employees";
    }

    @PostMapping
    public String saveEmployee(@ModelAttribute Employee employee, @RequestParam(required = false) Long departmentId) {
        if (departmentId != null) {
            departmentRepository.findById(departmentId).ifPresent(employee::setDepartment);
        }
        // Hash the username (which acts as password)
        if (employee.getUsername() != null && !employee.getUsername().isEmpty()) {
            employee.setUsername(passwordEncoder.encode(employee.getUsername()));
        }
        employeeRepository.save(employee);
        return "redirect:/employees";
    }

    @GetMapping("/delete/{id}")
    public String deleteEmployee(@PathVariable String id) {
        employeeRepository.deleteById(id);
        return "redirect:/employees";
    }

    @PostMapping("/bulk/assign-department")
    public String bulkAssignDepartment(@RequestParam("employeeIds") java.util.List<String> employeeIds,
            @RequestParam("departmentId") Long departmentId) {
        if (employeeIds != null && departmentId != null) {
            root.cyb.mh.attendancesystem.model.Department dept = departmentRepository.findById(departmentId)
                    .orElse(null);
            if (dept != null) {
                java.util.List<Employee> employees = employeeRepository.findAllById(employeeIds);
                for (Employee emp : employees) {
                    emp.setDepartment(dept);
                }
                employeeRepository.saveAll(employees);
            }
        }
        return "redirect:/employees";
    }

}
