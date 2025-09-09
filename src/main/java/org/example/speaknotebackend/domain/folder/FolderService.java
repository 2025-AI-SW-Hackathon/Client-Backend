package org.example.speaknotebackend.domain.folder;

import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.domain.folder.model.CreateFolderRequest;
import org.example.speaknotebackend.domain.repository.FolderRepository;
import org.example.speaknotebackend.domain.user.UserService;
import org.example.speaknotebackend.entity.Folder;
import org.example.speaknotebackend.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserService userService;

    @Transactional
    public Folder createFolder(Long userId, CreateFolderRequest request) {
        User owner = userService.findActiveById(userId);

        Folder folder = Folder.builder()
                .user(owner)
                .name(request.getFolderName())
                .build();

        Folder saved = folderRepository.save(folder);
        return saved;
    }
}
