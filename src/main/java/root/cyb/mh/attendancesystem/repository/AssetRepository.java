package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.Asset;

import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {
    List<Asset> findByStatus(Asset.Status status);

    boolean existsByAssetTag(String assetTag);

    List<Asset> findByCategory(Asset.Category category);

    long countByCategoryAndStatus(Asset.Category category, Asset.Status status);

    long countByStatus(Asset.Status status);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM Asset a LEFT JOIN a.assignedTo e WHERE " +
            "LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")

    List<Asset> search(@org.springframework.data.repository.query.Param("keyword") String keyword);

    List<Asset> findByAssignedTo(root.cyb.mh.attendancesystem.model.Employee employee);
}
