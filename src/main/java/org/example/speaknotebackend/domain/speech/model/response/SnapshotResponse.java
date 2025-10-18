package org.example.speaknotebackend.domain.speech.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

// 응답
@Data
@AllArgsConstructor
public class SnapshotResponse {
    private boolean ok;
    private Integer version;
}
