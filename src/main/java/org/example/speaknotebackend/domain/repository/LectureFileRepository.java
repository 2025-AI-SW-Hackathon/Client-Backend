package org.example.speaknotebackend.domain.repository;

import org.example.speaknotebackend.entity.LectureFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureFileRepository extends JpaRepository<LectureFile,Long> {
}
