package root.cyb.mh.attendancesystem.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., Dell Latitude 5420

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(unique = true)
    private String assetTag; // Unique internal ID

    private String serialNumber;

    private LocalDate purchaseDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Status status = Status.AVAILABLE;

    @ManyToOne
    @JoinColumn(name = "assigned_to_id")
    private Employee assignedTo; // Current holder (null if available)

    public enum Category {
        LAPTOP,
        MONITOR,
        KEYBOARD,
        MOUSE,
        HEADSET,
        PRINTER,
        PHONE,
        OTHER
    }

    public enum Status {
        AVAILABLE,
        ASSIGNED,
        BROKEN,
        LOST,
        RETIRED,
        REPAIR
    }
}
