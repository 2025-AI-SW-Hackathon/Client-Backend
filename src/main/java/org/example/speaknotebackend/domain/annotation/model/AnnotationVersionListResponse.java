package org.example.speaknotebackend.domain.annotation.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnotationVersionListResponse {
    private Long fileId;
    private int count;
    private java.util.List<AnnotationVersionItem> versions;
}
