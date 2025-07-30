package org.example.speaknotebackend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "LectureFile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @Column(length = 255, nullable = false)
    private String fileName;

    @Column(length = 512, nullable = false)
    private String fileUrl;
}