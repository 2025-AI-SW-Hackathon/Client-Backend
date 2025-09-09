package org.example.speaknotebackend.domain.repository;

import org.example.speaknotebackend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder,Long> {

    Folder findFirstByUserIdAndBasic(Long userId,Boolean True);
}
