package root.cyb.mh.attendancesystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalTime;

@Entity
@Data
public class WorkSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;
    @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;
    private int lateToleranceMinutes;
    private int earlyLeaveToleranceMinutes;

    // Store as comma-separated integers (1=Mon, 7=Sun)
    // Default: 6,7 (Saturday, Sunday)
    // Default: 6,7 (Saturday, Sunday)
    private String weekendDays = "6,7";

    private Integer defaultAnnualLeaveQuota = 12; // Days per year

    // Default constructor with standard values if needed
    public WorkSchedule() {
        this.startTime = LocalTime.of(9, 0);
        this.endTime = LocalTime.of(18, 0);
        this.lateToleranceMinutes = 15;
        this.earlyLeaveToleranceMinutes = 15;
    }
}
