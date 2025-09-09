package org.example.speaknotebackend.domain.folder;

import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.common.response.BaseResponseStatus;
import org.example.speaknotebackend.domain.folder.model.CreateFolderRequest;
import org.example.speaknotebackend.domain.folder.model.GetFolderListResponse;
import org.example.speaknotebackend.domain.folder.model.UpdateFolderNameRequest;
import org.example.speaknotebackend.domain.repository.FolderRepository;
import org.example.speaknotebackend.domain.user.UserService;
import org.example.speaknotebackend.entity.Folder;
import org.example.speaknotebackend.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public List<GetFolderListResponse> getFolderList(Long userId) {
        List<Folder> folders = folderRepository.findByUserId(userId);

        return folders.stream()
                .map(folder -> GetFolderListResponse.builder()
                        .folderId(folder.getId())
                        .name(folder.getName())
                        .build())
                .toList();
    }

    @Transactional
    public void updateFolder(Long userId, Long folderId, String folderName) {
        Folder folder = folderRepository.findByUserIdAndId(userId, folderId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FOLDER_NOT_FOUND));

        if (folderName.equals(folder.getName())) {
            return;
        }

        folder.setName(folderName); // TODO : 중복 폴더명 방지 로직 추가
    }
}
