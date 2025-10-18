package org.example.speaknotebackend.domain.annotation.model;
import java.time.Instant;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnnotationVersionItem {
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
