package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.Asset;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByStatus(Asset.Status status);

    List<Asset> findByCategory(Asset.Category category);

    long countByCategoryAndStatus(Asset.Category category, Asset.Status status);

    long countByStatus(Asset.Status status);
}
