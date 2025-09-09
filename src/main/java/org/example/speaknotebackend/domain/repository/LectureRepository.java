package org.example.speaknotebackend.domain.repository;

import org.example.speaknotebackend.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureRepository extends JpaRepository<Lecture,Long> {

    Lecture findByLectureFile_Id(Long fileId);
}
