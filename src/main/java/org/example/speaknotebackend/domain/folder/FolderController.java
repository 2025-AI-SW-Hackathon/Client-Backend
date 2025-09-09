package org.example.speaknotebackend.domain.folder;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.common.response.BaseResponse;
import org.example.speaknotebackend.common.response.BaseResponseStatus;
import org.example.speaknotebackend.domain.folder.model.CreateFolderRequest;
import org.example.speaknotebackend.config.UserDetailsImpl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.example.speaknotebackend.common.response.BaseResponseStatus.SUCCESS;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @Operation(
            summary = "폴더 생성",
            description = "사용자가 새로운 폴더를 생성합니다."
    )
    @PostMapping
    public BaseResponse<Void> createFolder(
            @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        if (user == null) {
            throw new BaseException(BaseResponseStatus.INVALID_USER_JWT);
        }

        folderService.createFolder(user.getUserId(), request);
        return new BaseResponse<>(SUCCESS);
    }
}
