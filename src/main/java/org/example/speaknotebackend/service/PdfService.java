package org.example.speaknotebackend.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.common.response.BaseResponseStatus;
import org.example.speaknotebackend.domain.repository.FolderRepository;
import org.example.speaknotebackend.domain.repository.LectureFileRepository;
import org.example.speaknotebackend.domain.repository.LectureRepository;
import org.example.speaknotebackend.domain.repository.UserRepository;
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

//    @Value("${custom.pdf.storage-dir}")
//    private String storageDir; // 저장 폴더(환경별 설정)

    @Value("${custom.pdf.allowed-origin}")
    private String fastapiBaseUrl; // 예: http://localhost:8000/upload

    private final UserService userService;
    private final UserRepository userRepository;
    private final LectureFileRepository lectureFileRepository;
    private final LectureRepository lectureRepository;
    private final FolderRepository folderRepository;

    @Value("${storage.mode:LOCAL}")        private String storageMode;     // LOCAL or GCS
    @Value("${storage.localDir:uploads}")  private String storageDir;
    @Value("${storage.gcs.bucket:}")       private String gcsBucket;
    @Value("${storage.gcs.prefix:pdf}")    private String gcsPrefix;

    private final Storage storage = StorageOptions.getDefaultInstance().getService(); // ADC

    private boolean useGcs() {
        return "GCS".equalsIgnoreCase(storageMode);
    }

    @Transactional
    public Long saveTempPDF(MultipartFile file, Long userId) {
        try {
            // 0) 파일명 안전 처리
            String original = (file.getOriginalFilename() == null) ? "uploaded.pdf" : file.getOriginalFilename();
            String sanitized = original.replaceAll("[\\\\/\\r\\n\\t]", "_"); // 경로/제어문자 방어
            String uuid = UUID.randomUUID().toString();

            String storedFileName = uuid + "_" + sanitized;   // 최종 저장명
            String fileUrl;                                    // DB에 저장할 URL/경로

            // 1) 업로드 (GCS or Local)
            if (useGcs()) {
                // ---- GCS 업로드 ----
                String objectName = (gcsPrefix == null || gcsPrefix.isBlank())
                        ? storedFileName
                        : gcsPrefix.replaceAll("^/|/$", "") + "/" + storedFileName;

                BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsBucket, objectName))
                        .setContentType(file.getContentType() == null ? "application/pdf" : file.getContentType())
                        .build();

                // InputStream 업로드
                try (var in = file.getInputStream()) {
                    storage.createFrom(blobInfo, in);
                }

                // gs:// 경로 저장(권장). 공개 URL이 필요하면 Signed URL을 별도 API에서 발급
                fileUrl = "gs://" + gcsBucket + "/" + objectName;

            } else {
                // ---- 로컬 저장 ----
                Path uploadDir = Paths.get(storageDir).toAbsolutePath().normalize();
                Files.createDirectories(uploadDir);

                Path filePath = uploadDir.resolve(storedFileName);
                try (var in = file.getInputStream()) {
                    Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                // 실제 파일 경로를 저장 (또는 Nginx 정적경로라면 공개 URL로 변환)
                fileUrl = filePath.toString();
            }

            // 2) 유저/폴더 조회
            if (userId == null) {
                System.out.println("❌ [PdfService] userId가 null - 비로그인 사용자");
                return null;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                System.out.println("❌ [PdfService] 사용자를 찾을 수 없음: " + userId);
                return null;
            }
            Folder folder = folderRepository.findFirstByUserIdAndBasic(user.getId(), true);
            if (folder == null) {
                System.out.println("⚠️ [PdfService] 기본 폴더 없음 → null로 저장(또는 생성 로직 추가 가능)");
            }

            // 3) DB 저장 (LectureFile → Lecture)
            LectureFile lectureFile = LectureFile.builder()
                    .uuid(uuid)
                    .fileName(sanitized)       // 원래 이름(정제)
                    .fileUrl(fileUrl)          // ✅ 실제 저장 위치(로컬 경로 or gs://bucket/object)
                    .build();
            LectureFile savedFile = lectureFileRepository.save(lectureFile);

            Lecture lecture = Lecture.builder()
                    .lectureFile(savedFile)
                    .summary("")
                    .tags("")
                    .folder(folder)
                    .lectureName(sanitized)
                    .language("ko")
                    .user(user)
                    .build();
            lectureRepository.save(lecture);

            System.out.println("✅ [PdfService] 저장 완료: fileId=" + savedFile.getId() + ", url=" + fileUrl);
            return savedFile.getId();

        } catch (IOException e) {
            // 업로드 실패
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
