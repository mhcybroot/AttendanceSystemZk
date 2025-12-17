package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import root.cyb.mh.attendancesystem.model.Device;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.DeviceRepository;
// import root.cyb.mh.attendancesystem.service.SyncService;

@Controller
@RequestMapping("/")
public class AttendanceController {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    // @Autowired
    // private SyncService syncService;

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("devices", deviceRepository.findAll());
        model.addAttribute("newDevice", new Device());
        return "device-status";
    }

    @PostMapping("/devices")
    public String addDevice(@ModelAttribute Device device) {
        deviceRepository.save(device);
        return "redirect:/devices";
    }

    @PostMapping("/sync")
    public String manualSync() {
        // syncService.syncAllDevices();
        return "redirect:/devices";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.service.AdmsService admsService;

    @PostMapping("/devices/download")
    public String downloadLogs() {
        // Queue command to download all logs
        // Command: DATA QUERY ATTLOG StartTime=2000-01-01 EndTime=2099-12-31
        // Simplified: DATA QUERY ATTLOG
        admsService.queueCommand("DATA QUERY ATTLOG");
        return "redirect:/devices";
    }

    @PostMapping("/devices/download-users")
    public String downloadUsers() {
        admsService.queueCommand("DATA QUERY USERINFO");
        return "redirect:/devices";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.repository.EmployeeRepository employeeRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.DepartmentRepository departmentRepository;

    @GetMapping("/attendance")
    public String attendance(@RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        // 1. Prepare Employee Map for Name Lookup (needed for sorting by name)
        java.util.Map<String, String> employeeMap = new java.util.HashMap<>();
        for (root.cyb.mh.attendancesystem.model.Employee emp : employeeRepository.findAll()) {
            employeeMap.put(emp.getId(), emp.getName());
        }

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
        // Handle custom sort field "employeeName" which is not in DB
        if ("employeeName".equals(sortField)) {
            // Fallback: sort by ID if name is selected, as we can't easily DB-sort by
            // transient name map
            sort = Sort.by("id").descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<root.cyb.mh.attendancesystem.model.AttendanceLog> logsPage;

        if (departmentId != null) {
            logsPage = attendanceLogRepository.findByEmployeeDepartmentId(departmentId, pageable);
            model.addAttribute("selectedDeptId", departmentId);
        } else {
            logsPage = attendanceLogRepository.findAll(pageable);
        }

        model.addAttribute("logs", logsPage.getContent());
        model.addAttribute("page", logsPage);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("employeeMap", employeeMap);

        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "attendance";
    }
}
