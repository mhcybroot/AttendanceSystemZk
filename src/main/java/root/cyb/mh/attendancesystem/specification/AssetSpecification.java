package root.cyb.mh.attendancesystem.specification;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import root.cyb.mh.attendancesystem.model.Asset;
import root.cyb.mh.attendancesystem.model.Employee;

import java.util.ArrayList;
import java.util.List;

public class AssetSpecification {

    public static Specification<Asset> filterBy(String keyword, Asset.Category category, Asset.Status status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Keyword Search (Name, Tag, or Employee Name)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), likePattern);
                Predicate tagMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("assetTag")), likePattern);

                // Join for Employee Name
                // We use left join to include unassigned assets if they match by tag/name
                // But for employee match, obviously they must be assigned.
                // However, we need to be careful with criteria builder logic.
                // Simplest is to just check if assignedTo is present for employee match.

                // For simplicity in Specification, let's keep it robust.
                // Note: The original @Query used a Left Join.

                Predicate employeeMatch = criteriaBuilder.like(
                        criteriaBuilder
                                .lower(root.join("assignedTo", jakarta.persistence.criteria.JoinType.LEFT).get("name")),
                        likePattern);

                predicates.add(criteriaBuilder.or(nameMatch, tagMatch, employeeMatch));
            }

            // 2. Category Filter
            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category));
            }

            // 3. Status Filter
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
