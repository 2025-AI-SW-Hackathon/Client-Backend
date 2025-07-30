package org.example.speaknotebackend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "UserStatistic")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserStatistic extends BaseEntity {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Column(nullable = false)
    private Integer totalLecture;

    @Column(nullable = false)
    private Long totalLearningTime;

    @Column(nullable = false)
    private Integer streakDay;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal autoAnnotationRate;
}
