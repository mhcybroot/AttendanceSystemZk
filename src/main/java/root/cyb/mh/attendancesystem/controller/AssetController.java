package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Asset;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.AssetAssignmentRepository;
import root.cyb.mh.attendancesystem.repository.AssetRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.AssetService;

import java.util.List;

@Controller
@RequestMapping("/admin/assets")
public class AssetController {

    @Autowired
    private AssetService assetService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetAssignmentRepository assetAssignmentRepository;

    @GetMapping
    public String listAssets(Model model) {
        List<Asset> assets = assetService.getAllAssets();
        List<Employee> employees = employeeRepository.findAll();

        model.addAttribute("assets", assets);
        model.addAttribute("employees", employees);
        model.addAttribute("stats", assetService.getInventoryStats());
        model.addAttribute("newAsset", new Asset());
        model.addAttribute("activeLink", "assets");

        return "admin-assets";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.repository.AssetStatusLogRepository statusLogRepository;

    @GetMapping("/{id}/history")
    public String viewAssetHistory(@PathVariable Long id, Model model) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Asset ID"));

        // 1. Assignments
        List<root.cyb.mh.attendancesystem.model.AssetAssignment> assignments = assetAssignmentRepository
                .findByAssetIdOrderByAssignedDateDesc(id);

        // 2. Status Logs
        List<root.cyb.mh.attendancesystem.model.AssetStatusLog> statusLogs = statusLogRepository
                .findByAssetIdOrderByChangedDateDesc(id);

        // 3. Merge into Timeline
        List<root.cyb.mh.attendancesystem.dto.AssetTimelineDTO> timeline = new java.util.ArrayList<>();

        // Map Assignments
        for (root.cyb.mh.attendancesystem.model.AssetAssignment a : assignments) {
            String durationStr = "";
            if (a.getReturnDate() != null) {
                java.time.Duration d = java.time.Duration.between(a.getAssignedDate(), a.getReturnDate());
                long days = d.toDays();
                long hours = d.toHours() % 24;
                if (days > 0)
                    durationStr = days + " days";
                else
                    durationStr = hours + " hours";
            } else {
                java.time.Duration d = java.time.Duration.between(a.getAssignedDate(), java.time.LocalDateTime.now());
                long days = d.toDays();
                if (days > 0)
                    durationStr = days + " days (Active)";
                else
                    durationStr = "Just now";
            }

            timeline.add(root.cyb.mh.attendancesystem.dto.AssetTimelineDTO.builder()
                    .timestamp(a.getAssignedDate())
                    .type(root.cyb.mh.attendancesystem.dto.AssetTimelineDTO.Type.ASSIGNMENT)
                    .employee(a.getEmployee())
                    .returnDate(a.getReturnDate())
                    .description(a.getConditionOnAssign()) // Initial condition
                    .returnCondition(a.getConditionOnReturn())
                    .duration(durationStr)
                    .build());

            // If returned, maybe add a separate event?
            // Or just keep it as one "Assignment" block.
            // Let's stick to the Assignment Block logic for now,
            // but ensuring the sort uses the start date.
        }

        // Map Status Changes
        for (root.cyb.mh.attendancesystem.model.AssetStatusLog l : statusLogs) {
            timeline.add(root.cyb.mh.attendancesystem.dto.AssetTimelineDTO.builder()
                    .timestamp(l.getChangedDate())
                    .type(root.cyb.mh.attendancesystem.dto.AssetTimelineDTO.Type.STATUS_CHANGE)
                    .oldStatus(l.getOldStatus())
                    .newStatus(l.getNewStatus())
                    .description(l.getReason())
                    .build());
        }

        // Sort
        java.util.Collections.sort(timeline);

        model.addAttribute("asset", asset);
        model.addAttribute("timeline", timeline);
        model.addAttribute("activeLink", "assets");

        return "admin-asset-history";
    }

    @PostMapping("/save")
    public String saveAsset(@ModelAttribute Asset asset) {
        // Handle Edit vs New (if ID exists, JPA updates)
        // Note: For simple edit, we might lose status if not careful,
        // but here we trust the form or add logic to preserve field if null.
        // For Quick MVP: trust form.

        if (asset.getId() != null) {
            // Preserve existing status/assignment if form didn't send them
            Asset existing = assetRepository.findById(asset.getId()).orElse(null);
            if (existing != null) {
                if (asset.getStatus() == null)
                    asset.setStatus(existing.getStatus());
                if (asset.getAssignedTo() == null)
                    asset.setAssignedTo(existing.getAssignedTo());
            }
        }

        assetService.saveAsset(asset);
        return "redirect:/admin/assets";
    }

    @PostMapping("/assign")
    public String assignAsset(@RequestParam Long assetId, @RequestParam String employeeId,
            @RequestParam String condition) {
        assetService.assignAsset(assetId, employeeId, condition);
        return "redirect:/admin/assets";
    }

    @PostMapping("/return")
    public String returnAsset(@RequestParam Long assetId, @RequestParam String condition) {
        assetService.returnAsset(assetId, condition);
        return "redirect:/admin/assets";
    }

    @GetMapping("/delete/{id}")
    public String deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return "redirect:/admin/assets";
    }
}
