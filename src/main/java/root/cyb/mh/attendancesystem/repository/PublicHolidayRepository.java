package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import root.cyb.mh.attendancesystem.model.PublicHoliday;
import java.util.List;
import org.springframework.data.domain.Sort;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    List<PublicHoliday> findAll(Sort sort);
}
