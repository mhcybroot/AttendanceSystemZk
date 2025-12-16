package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import root.cyb.mh.attendancesystem.model.WorkSchedule;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
}
