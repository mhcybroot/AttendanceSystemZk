package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import root.cyb.mh.attendancesystem.model.AssetAssignment;

import java.util.List;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {
    List<AssetAssignment> findByAssetIdOrderByAssignedDateDesc(Long assetId);

    List<AssetAssignment> findByEmployeeIdOrderByAssignedDateDesc(String employeeId);

    @Transactional
    void deleteByAssetId(Long assetId);
}
