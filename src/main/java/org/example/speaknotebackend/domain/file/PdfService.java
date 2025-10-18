package org.example.speaknotebackend.domain.file;

import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.common.response.BaseResponseStatus;
import org.example.speaknotebackend.domain.folder.FolderRepository;
import org.example.speaknotebackend.domain.lecture.LectureRepository;
import org.example.speaknotebackend.domain.user.UserRepository;
import org.example.speaknotebackend.domain.user.UserService;
import org.example.speaknotebackend.entity.Folder;
import org.example.speaknotebackend.entity.Lecture;
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
import java.util.UUID;
import java.util.List;

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
    private final LectureRepository lectureRepository;
    private final FolderRepository folderRepository;

    @Transactional
    public Long saveTempPDF(MultipartFile file, Long userId) {
        try {
            Path uploadDir = Paths.get(storageDir);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }


            // 원래 파일명
            String uuid = UUID.randomUUID().toString();
            String storedFileName = file.getOriginalFilename();
            Path filePath = uploadDir.resolve(uuid+"_"+storedFileName);
            Path realfilePath = uploadDir;
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            if (userId != null) {
                System.out.println("🔍 [PdfService] userId가 존재함: " + userId);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    System.out.println("🔍 [PdfService] 사용자 찾음: " + user.getEmail());
                    Folder folder = folderRepository.findFirstByUserIdAndBasic(user.getId(),true);
                    System.out.println("🔍 [PdfService] 폴더 찾음: " + (folder != null ? folder.getName() : "null"));
                    
                    LectureFile lectureFile = LectureFile.builder()
                            .uuid(uuid)
                            .fileName(storedFileName)
                            .fileUrl(realfilePath.toString()) // 필요 시 공개 URL/Signed URL로 대체
                            .build();
                    LectureFile saved = lectureFileRepository.save(lectureFile);
                    System.out.println("✅ [PdfService] LectureFile 저장됨: " + saved.getId());
                    
                    Lecture lecture = Lecture.builder()
                            .lectureFile(saved)
                            .summary("")
                            .tags("")
                            .folder(folder)
                            .lectureName(storedFileName)
                            .language("ko")
                            .user(user)
                            .build();
                    Lecture lecture1 = lectureRepository.save(lecture);
                    System.out.println("✅ [PdfService] Lecture 저장됨: " + lecture1.getId());
                    System.out.println(lecture1);
                    System.out.println(lecture1.getEndedAt());
                    return saved.getId();
                } else {
                    System.out.println("❌ [PdfService] 사용자를 찾을 수 없음: " + userId);
                }
            } else {
                System.out.println("❌ [PdfService] userId가 null - 비로그인 사용자");
            }
            // 비로그인/유저없음인 경우엔 null 리턴(컨트롤러에서 처리)
            return 0L;

        } catch (IOException e) {
            throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
        }
    }

    /**
     * FastAPI로 파일 + userId + fileId + sessionId를 multipart/form-data로 전송
     */
    public String sendPdfFileToFastAPI(MultipartFile file, Long userId, Long fileId, String sessionId) {
        try {
            String boundary = "----SpringToFastAPI" + System.currentTimeMillis();
            HttpClient client = HttpClient.newHttpClient();

            // part: 일반 폼 필드 생성기 (게스트는 0으로 전송)
            byte[] userIdPart = buildFormField(boundary, "userId", String.valueOf(userId));
            byte[] fileIdPart = buildFormField(boundary, "fileId", String.valueOf(fileId));
            byte[] sessionIdPart = buildFormField(boundary, "sessionId", sessionId);

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
                    sessionIdPart,
                    fileHeader, fileBytes, fileTail,
                    endBoundary
            );
            // 호출 URL 보정: /pdf 경로가 보장되도록 처리
            String targetUrl = (fastapiBaseUrl == null || fastapiBaseUrl.isBlank())
                    ? "http://localhost:8000/pdf"
                    : (fastapiBaseUrl.endsWith("/pdf") ? fastapiBaseUrl : fastapiBaseUrl + "/pdf");

            System.out.println("[PdfService] FastAPI target URL: " + targetUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();
            System.out.println("[PdfService] FastAPI status=" + status + ", body=" + body);
            // 비-JSON / 에러 상태 보호 반환
            if (status < 200 || status >= 300) {
                return "{" +
                        "\"ok\":false,\"status\":" + status + ",\"body\":\"" + body.replace("\"", "\\\"") + "\"}";
            }
            return body;

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

    /**
     * FastAPI 응답에서 받은 요약/키워드를 Lecture에 반영한다.
     */
    @Transactional
    public void updateLectureMetaFromPythonResponse(Long fileId, String summary, List<String> keywords) {
        if (fileId == null) return;

        Lecture lecture = lectureRepository.findByLectureFile_Id(fileId);
        if (lecture == null) return;

        // summary가 유효하게 들어온 경우에만 업데이트 (255자 제한)
        if (summary != null && !summary.isBlank()) {
            String trimmed = summary.trim();
            if (trimmed.length() > 255) trimmed = trimmed.substring(0, 255);
            lecture.setSummary(trimmed);
        }

        // keywords가 존재할 때만 tags 업데이트 (255자 제한)
        if (keywords != null && !keywords.isEmpty()) {
            String tagsJoined = String.join(",", keywords).trim();
            if (!tagsJoined.isBlank()) {
                if (tagsJoined.length() > 255) tagsJoined = tagsJoined.substring(0, 255);
                lecture.setTags(tagsJoined);
            }
        }

        lectureRepository.save(lecture);
    }
}
