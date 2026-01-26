package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import root.cyb.mh.attendancesystem.model.AssetStatusLog;

import java.util.List;

@Repository
public interface AssetStatusLogRepository extends JpaRepository<AssetStatusLog, Long> {
    List<AssetStatusLog> findByAssetIdOrderByChangedDateDesc(Long assetId);

    @Transactional
    void deleteByAssetId(Long assetId);
}
