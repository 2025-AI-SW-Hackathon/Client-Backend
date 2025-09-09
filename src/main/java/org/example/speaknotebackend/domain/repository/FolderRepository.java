package org.example.speaknotebackend.domain.repository;

import org.example.speaknotebackend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserId(Long userId);
}


