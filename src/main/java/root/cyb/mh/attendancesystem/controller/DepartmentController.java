package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import root.cyb.mh.attendancesystem.model.Department;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository;

@Controller
public class DepartmentController {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.EmployeeRepository employeeRepository;

    @GetMapping("/departments")
    public String departments(Model model,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir) {

        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("asc")
                ? org.springframework.data.domain.Sort.by(sortField).ascending()
                : org.springframework.data.domain.Sort.by(sortField).descending();

        model.addAttribute("departments", departmentRepository.findAll(sort));

        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "departments";
    }

    @PostMapping("/departments")
    public String saveDepartment(@RequestParam(required = false) Long id, @RequestParam String name,
            @RequestParam String description) {
        Department dept;
        if (id != null) {
            dept = departmentRepository.findById(id).orElse(new Department());
        } else {
            dept = new Department();
        }
        dept.setName(name);
        dept.setDescription(description);
        departmentRepository.save(dept);
        return "redirect:/departments";
    }

    @GetMapping("/departments/delete/{id}")
    public String deleteDepartment(@PathVariable Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        // Check if employees exist in this department
        if (!employeeRepository.findByDepartmentId(id).isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete department. It has assigned employees.");
            return "redirect:/departments";
        }
        departmentRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Department deleted successfully.");
        return "redirect:/departments";
    }
}
