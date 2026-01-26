package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.Asset;
import root.cyb.mh.attendancesystem.model.AssetAssignment;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.AssetAssignmentRepository;
import root.cyb.mh.attendancesystem.repository.AssetRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssetService {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetAssignmentRepository assignmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.AssetStatusLogRepository statusLogRepository;

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public List<Asset> searchAssets(String keyword) {
        return assetRepository.search(keyword);
    }

    public List<Asset> getAssetsByStatus(String statusStr) {
        if (statusStr == null || statusStr.isEmpty()) {
            return getAllAssets();
        }

        if ("UNAVAILABLE".equalsIgnoreCase(statusStr)) {
            // Fetch all non-active statuses
            java.util.List<Asset> all = getAllAssets();
            return all.stream()
                    .filter(a -> a.getStatus() == Asset.Status.BROKEN ||
                            a.getStatus() == Asset.Status.LOST ||
                            a.getStatus() == Asset.Status.RETIRED ||
                            a.getStatus() == Asset.Status.REPAIR)
                    .collect(java.util.stream.Collectors.toList());
        }

        try {
            Asset.Status status = Asset.Status.valueOf(statusStr.toUpperCase());
            return assetRepository.findByStatus(status);
        } catch (IllegalArgumentException e) {
            return getAllAssets();
        }
    }

    public void saveAsset(Asset asset) {
        // Check for updates to log status changes
        if (asset.getId() != null) {
            assetRepository.findById(asset.getId()).ifPresent(existing -> {
                if (existing.getStatus() != asset.getStatus()) {
                    root.cyb.mh.attendancesystem.model.AssetStatusLog log = new root.cyb.mh.attendancesystem.model.AssetStatusLog();
                    log.setAsset(existing); // Use existing reference or re-referenced asset
                    log.setOldStatus(existing.getStatus());
                    log.setNewStatus(asset.getStatus());
                    log.setReason("Updated via Edit Form");
                    log.setChangedDate(LocalDateTime.now());
                    statusLogRepository.save(log);
                }
            });
        }
        assetRepository.save(asset);
    }

    public void assignAsset(Long assetId, String employeeId, String condition) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Asset ID"));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Employee ID"));

        if (asset.getStatus() == Asset.Status.ASSIGNED) {
            throw new IllegalStateException("Asset is already assigned.");
        }

        // Update Asset
        asset.setAssignedTo(employee);
        asset.setStatus(Asset.Status.ASSIGNED);
        assetRepository.save(asset);

        // Record History
        AssetAssignment assignment = new AssetAssignment();
        assignment.setAsset(asset);
        assignment.setEmployee(employee);
        assignment.setAssignedDate(LocalDateTime.now());
        assignment.setConditionOnAssign(condition);
        assignmentRepository.save(assignment);
    }

    public void returnAsset(Long assetId, String condition) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Asset ID"));

        if (asset.getStatus() != Asset.Status.ASSIGNED || asset.getAssignedTo() == null) {
            throw new IllegalStateException("Asset is not currently assigned.");
        }

        Employee previousOwner = asset.getAssignedTo();

        // Update Asset
        asset.setAssignedTo(null);
        asset.setStatus(Asset.Status.AVAILABLE);
        assetRepository.save(asset);

        // Update History (Find active assignment)
        List<AssetAssignment> history = assignmentRepository.findByAssetIdOrderByAssignedDateDesc(assetId);
        // The most recent one should be the active one
        if (!history.isEmpty()) {
            AssetAssignment currentAssignment = history.get(0);
            if (currentAssignment.getReturnDate() == null) {
                currentAssignment.setReturnDate(LocalDateTime.now());
                currentAssignment.setConditionOnReturn(condition);
                assignmentRepository.save(currentAssignment);
            }
        }
    }

    public Map<String, Long> getInventoryStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", assetRepository.count());
        stats.put("available", assetRepository.countByStatus(Asset.Status.AVAILABLE));
        stats.put("assigned", assetRepository.countByStatus(Asset.Status.ASSIGNED));

        long unavailable = assetRepository.countByStatus(Asset.Status.BROKEN) +
                assetRepository.countByStatus(Asset.Status.LOST) +
                assetRepository.countByStatus(Asset.Status.RETIRED) +
                assetRepository.countByStatus(Asset.Status.REPAIR);

        stats.put("broken", unavailable); // Using 'broken' key to match frontend template
        return stats;
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteAsset(Long assetId) {
        // Delete history first to avoid Foreign Key Constraint fail
        assignmentRepository.deleteByAssetId(assetId);
        statusLogRepository.deleteByAssetId(assetId);
        assetRepository.deleteById(assetId);
    }

    public void bulkCreateAssets(String name, Asset.Category category, String startTag, int quantity,
            String description) {
        String prefix = startTag.replaceAll("[0-9]", "");
        String numberPart = startTag.replaceAll("[^0-9]", "");

        int startNumber = 0;
        int numberLength = 0;

        if (!numberPart.isEmpty()) {
            startNumber = Integer.parseInt(numberPart);
            numberLength = numberPart.length();
        }

        for (int i = 0; i < quantity; i++) {
            Asset asset = new Asset();
            asset.setName(name);
            asset.setCategory(category);
            asset.setDescription(description);
            asset.setStatus(Asset.Status.AVAILABLE);

            if (!numberPart.isEmpty()) {
                int currentNumber = startNumber + i;
                String format = "%0" + numberLength + "d";
                String newTag = prefix + String.format(format, currentNumber);

                // Check for duplicate
                if (assetRepository.existsByAssetTag(newTag)) {
                    throw new IllegalArgumentException(
                            "Asset Tag '" + newTag + "' already exists. Bulk operation aborted.");
                }

                asset.setAssetTag(newTag);
            } else {
                // Fallback if no specific number pattern (append index)
                String fallbackTag = startTag + "-" + (i + 1);
                if (assetRepository.existsByAssetTag(fallbackTag)) {
                    throw new IllegalArgumentException(
                            "Asset Tag '" + fallbackTag + "' already exists. Bulk operation aborted.");
                }
                asset.setAssetTag(fallbackTag);
            }

            saveAsset(asset);
        }
    }
}
