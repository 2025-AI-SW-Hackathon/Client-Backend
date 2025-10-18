package org.example.speaknotebackend.domain.lecture.model;


import org.example.speaknotebackend.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.List;

public record LectureHistoryFilter(
        String q, Long folderId, BaseEntity.Status status,
        LocalDateTime from, LocalDateTime to, List<String> tags,
        boolean withAnno
) {}

