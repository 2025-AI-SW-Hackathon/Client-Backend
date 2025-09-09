package org.example.speaknotebackend.domain.repository;

import org.example.speaknotebackend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {
}


