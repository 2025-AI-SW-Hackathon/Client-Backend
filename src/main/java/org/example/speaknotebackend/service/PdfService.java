package org.example.speaknotebackend.service;

import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.common.response.BaseResponseStatus;
import org.example.speaknotebackend.domain.repository.LectureFileRepository;
import org.example.speaknotebackend.domain.repository.UserRepository;
import org.example.speaknotebackend.domain.user.UserService;
import org.example.speaknotebackend.entity.LectureFile;
import org.example.speaknotebackend.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {

    @Value("${custom.pdf.storage-dir}")
    private String storageDir; // 저장 폴더(환경별 설정)

    @Value("${custom.pdf.allowed-origin}")
    private String fastapiBaseUrl; // 예: http://localhost:8000/upload

    private final UserService userService;
    private final UserRepository userRepository;
    private final LectureFileRepository lectureFileRepository;

    @Transactional
    public Long saveTempPDF(MultipartFile file, Long userId) {
        try {
            Path uploadDir = Paths.get(storageDir);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalName = file.getOriginalFilename();
            String uuid = UUID.randomUUID().toString();
            String storedFileName = uuid + "_" + (originalName == null ? "uploaded.pdf" : originalName);
            Path filePath = uploadDir.resolve(storedFileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    LectureFile lectureFile = LectureFile.builder()
                            .user(user)
                            .fileName(storedFileName)
                            .fileUrl(filePath.toString()) // 필요 시 공개 URL/Signed URL로 대체
                            .build();
                    LectureFile saved = lectureFileRepository.save(lectureFile);
                    return saved.getId();
                }
            }
            // 비로그인/유저없음인 경우엔 null 리턴(컨트롤러에서 처리)
            return null;

        } catch (IOException e) {
            throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
        }
    }

    /**
     * FastAPI로 파일 + userId + fileId를 multipart/form-data로 전송
     */
    public String sendPdfFileToFastAPI(MultipartFile file, Long userId, Long fileId) {
        try {
            String boundary = "----SpringToFastAPI" + System.currentTimeMillis();
            HttpClient client = HttpClient.newHttpClient();

            // part: 일반 폼 필드 생성기
            byte[] userIdPart = buildFormField(boundary, "userId", userId == null ? "" : String.valueOf(userId));
            byte[] fileIdPart = buildFormField(boundary, "fileId", fileId == null ? "" : String.valueOf(fileId));

            // part: 파일
            String fileName = file.getOriginalFilename() == null ? "uploaded.pdf" : file.getOriginalFilename();
            String mimeType = file.getContentType() == null ? "application/pdf" : file.getContentType();

            byte[] fileHeader = (
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                            "Content-Type: " + mimeType + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8);

            byte[] fileBytes = file.getBytes();
            byte[] fileTail = "\r\n".getBytes(StandardCharsets.UTF_8);

            byte[] endBoundary = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

            byte[] requestBody = concatenate(
                    userIdPart,
                    fileIdPart,
                    fileHeader, fileBytes, fileTail,
                    endBoundary
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fastapiBaseUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();

        } catch (Exception e) {
            e.printStackTrace();
            return "FastAPI 호출 실패";
        }
    }

    /** 일반 텍스트 필드 part */
    private byte[] buildFormField(String boundary, String name, String value) {
        String part =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                        (value == null ? "" : value) + "\r\n";
        return part.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] concatenate(byte[]... parts) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            outputStream.write(part);
        }
        return outputStream.toByteArray();
    }
}
