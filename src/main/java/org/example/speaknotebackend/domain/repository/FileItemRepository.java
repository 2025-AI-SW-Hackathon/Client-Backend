package org.example.speaknotebackend.domain.repository;


import org.apache.tomcat.util.http.fileupload.FileItem;
import org.example.speaknotebackend.entity.LectureFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileItemRepository extends JpaRepository<LectureFile, Long> {
    Optional<LectureFile> findByIdAndUserId(Long id, Long ownerUserId);
}
