package org.example.speaknotebackend.domain.folder.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

    @Max(value = 20, message = "폴더명은 최대 20자입니다.")
    @Schema(description = "폴더명", example = "새로운 폴더") // null이면 "새로운 폴더"로 폴더 이름 세팅
    private String folderName;
}
