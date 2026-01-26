package root.cyb.mh.attendancesystem.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    private Asset.Status oldStatus;

    @Enumerated(EnumType.STRING)
    private Asset.Status newStatus;

    @Column(columnDefinition = "TEXT")
    private String reason; // Notes from the edit form or auto-generated

    private LocalDateTime changedDate = LocalDateTime.now();

    // Who changed it? (Could add User ID later, for now internal/admin)
}
