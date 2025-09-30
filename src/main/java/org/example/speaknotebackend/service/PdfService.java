package org.example.speaknotebackend.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    @Value("${custom.pdf.allowed-origin}")
    private String fastapiBaseUrl; // 예: http://localhost:8000/upload

    private final UserService userService;
    private final UserRepository userRepository;
    private final LectureFileRepository lectureFileRepository;
    private final LectureRepository lectureRepository;
    private final FolderRepository folderRepository;
    private final Storage storage;
    @Value("${storage.mode}")
    private String storageMode;     // LOCAL or GCS
    @Value("${storage.localDir:uploads}")  private String storageDir;
    @Value("${storage.gcs.bucket}")       private String gcsBucket;
    @Value("${storage.gcs.prefix:pdf}")   private String gcsPrefix;


    private boolean useGcs() {
        return "GCS".equalsIgnoreCase(storageMode);
    }

    @Transactional
    public Long saveTempPDF(MultipartFile file, Long userId) {
        final long t0 = System.currentTimeMillis();
        log.info("[saveTempPDF] start storageMode={}, gcsBucket={}, gcsPrefix={}",
                storageMode, gcsBucket, gcsPrefix);

        try {
            // 0) 입력 파라미터 점검
            if (file == null || file.isEmpty()) {
                log.warn("[saveTempPDF] invalid file: null or empty");
                throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
            }
            log.debug("[saveTempPDF] file originalName={}, size={}, contentType={}",
                    safeName(file.getOriginalFilename()), safeSize(file), file.getContentType());

            // 1) 파일명 정규화
            String original = (file.getOriginalFilename() == null) ? "uploaded.pdf" : file.getOriginalFilename();
            String sanitized = original.replaceAll("[\\\\/\\r\\n\\t]", "_");
            String uuid = UUID.randomUUID().toString();
            String storedFileName = uuid + "_" + sanitized;
            String fileUrl;

            log.info("[saveTempPDF] prepared names uuid={}, sanitizedName={}", shortUuid(uuid), sanitized);

            // 2) 저장 수행 (GCS or Local)
            if (useGcs()) {
                String objectName = (gcsPrefix == null || gcsPrefix.isBlank())
                        ? storedFileName
                        : gcsPrefix.replaceAll("^/|/$", "") + "/" + storedFileName;

                BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsBucket, objectName))
                        .setContentType(file.getContentType() == null ? "application/pdf" : file.getContentType())
                        .build();

                log.info("[saveTempPDF] GCS upload -> bucket={}, object={}", gcsBucket, objectName);

                try (var in = file.getInputStream()) {
                    storage.createFrom(blobInfo, in);
                } catch (Exception gcsEx) {
                    log.error("[saveTempPDF] GCS upload failed bucket={}, object={}, reason={}",
                            gcsBucket, objectName, gcsEx.getMessage(), gcsEx);
                    throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
                }

                fileUrl = "gs://" + gcsBucket + "/" + objectName;
                log.info("[saveTempPDF] GCS upload success url={}", fileUrl);

            } else {
                Path uploadDir = Paths.get(storageDir).toAbsolutePath().normalize();
                log.debug("[saveTempPDF] local uploadDir={}", uploadDir);
                try {
                    Files.createDirectories(uploadDir);
                } catch (IOException mkEx) {
                    log.error("[saveTempPDF] createDirectories failed dir={}, reason={}", uploadDir, mkEx.getMessage(), mkEx);
                    throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
                }

                Path filePath = uploadDir.resolve(storedFileName);
                try (var in = file.getInputStream()) {
                    Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ioEx) {
                    log.error("[saveTempPDF] local copy failed path={}, reason={}", filePath, ioEx.getMessage(), ioEx);
                    throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
                }
                fileUrl = filePath.toString();
                log.info("[saveTempPDF] Local save success path={}", fileUrl);
            }

            // 3) 유저/폴더 조회
            if (userId == null) {
                log.warn("[saveTempPDF] userId is null -> guest upload? returning null. elapsedMs={}",
                        (System.currentTimeMillis() - t0));
                return null;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("[saveTempPDF] user not found userId={}. elapsedMs={}", userId, (System.currentTimeMillis() - t0));
                return null;
            }
            log.debug("[saveTempPDF] user found id={}, email={}", user.getId(), maskEmail(user.getEmail()));

            Folder folder = folderRepository.findFirstByUserIdAndBasic(user.getId(), true);
            if (folder == null) {
                log.info("[saveTempPDF] basic folder not found for userId={} -> lecture.folder=null (you may create later)", user.getId());
            } else {
                log.debug("[saveTempPDF] basic folder found id={}", folder.getId());
            }

            // 4) DB 저장 (LectureFile -> Lecture)
            LectureFile lectureFile = LectureFile.builder()
                    .uuid(uuid)
                    .fileName(sanitized)
                    .fileUrl(fileUrl)
                    .build();

            LectureFile savedFile = lectureFileRepository.save(lectureFile);
            log.info("[saveTempPDF] LectureFile saved id={}, uuid={}", savedFile.getId(), shortUuid(savedFile.getUuid()));

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
            log.info("[saveTempPDF] Lecture saved (fileId={}, userId={}, folderId={}), elapsedMs={}",
                    savedFile.getId(), user.getId(), (folder != null ? folder.getId() : null),
                    (System.currentTimeMillis() - t0));

            return savedFile.getId();

        } catch (BaseException be) {
            // 이미 상세 로그를 남겼으므로 그대로 전파
            log.error("[saveTempPDF] BaseException thrown code={}, msg={}, elapsedMs={}",
                    be.getStatus().getCode(), be.getStatus().getMessage(), (System.currentTimeMillis() - t0));
            throw be;
        } catch (Exception e) {
            log.error("[saveTempPDF] unexpected error reason={}, elapsedMs={}", e.getMessage(), (System.currentTimeMillis() - t0), e);
            throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
        }
    }

    /**
     * FastAPI로 파일 + userId + fileId + sessionId를 multipart/form-data로 전송
     */
    public String sendPdfFileToFastAPI(MultipartFile file, Long userId, Long fileId, String sessionId) {
        final long t0 = System.currentTimeMillis();
        log.info("[sendPdfFileToFastAPI] start target={}, userId={}, fileId={}, sessionId={}",
                fastapiBaseUrl, userId, fileId, maskSession(sessionId));

        try {
            if (file == null || file.isEmpty()) {
                log.warn("[sendPdfFileToFastAPI] invalid file: null or empty");
                return "Invalid file";
            }

            String boundary = "----SpringToFastAPI" + System.currentTimeMillis();
            HttpClient client = HttpClient.newHttpClient();

            // part: 일반 필드
            byte[] userIdPart = buildFormField(boundary, "userId", String.valueOf(userId));
            byte[] fileIdPart = buildFormField(boundary, "fileId", String.valueOf(fileId));
            byte[] sessionIdPart = buildFormField(boundary, "session_id", sessionId);

            // part: 파일
            String fileName = file.getOriginalFilename() == null ? "uploaded.pdf" : file.getOriginalFilename();
            String mimeType = file.getContentType() == null ? "application/pdf" : file.getContentType();
            log.debug("[sendPdfFileToFastAPI] fileName={}, size={}, mimeType={}", safeName(fileName), safeSize(file), mimeType);

            byte[] fileHeader = (
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                            "Content-Type: " + mimeType + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8);

            byte[] fileBytes = file.getBytes();
            byte[] fileTail = "\r\n".getBytes(StandardCharsets.UTF_8);
            byte[] endBoundary = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

            byte[] requestBody = concatenate(
                    userIdPart, fileIdPart, sessionIdPart,
                    fileHeader, fileBytes, fileTail, endBoundary
            );
            log.info("[sendPdfFileToFastAPI] built multipart body bytes={}", requestBody.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fastapiBaseUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            log.info("[sendPdfFileToFastAPI] sending request to FastAPI…");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String preview = preview(response.body(), 200);
            log.info("[sendPdfFileToFastAPI] response status={}, bodyPreview={}..., elapsedMs={}",
                    status, preview, (System.currentTimeMillis() - t0));

            if (status >= 400) {
                log.warn("[sendPdfFileToFastAPI] non-2xx from FastAPI status={}, bodyHead={}", status, preview);
            }
            return response.body();

        } catch (Exception e) {
            log.error("[sendPdfFileToFastAPI] failed reason={}, elapsedMs={}", e.getMessage(), (System.currentTimeMillis() - t0), e);
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
        final long t0 = System.currentTimeMillis();
        log.info("[updateLectureMetaFromPythonResponse] start fileId={}, summaryPresent={}, keywordsCount={}",
                fileId, summary != null && !summary.isBlank(), (keywords == null ? 0 : keywords.size()));

        if (fileId == null) {
            log.warn("[updateLectureMetaFromPythonResponse] fileId is null → skip");
            return;
        }

        Lecture lecture = lectureRepository.findByLectureFile_Id(fileId);
        if (lecture == null) {
            log.warn("[updateLectureMetaFromPythonResponse] lecture not found by fileId={}", fileId);
            return;
        }

        // summary 업데이트 (255 제한)
        if (summary != null && !summary.isBlank()) {
            String trimmed = summary.trim();
            if (trimmed.length() > 255) trimmed = trimmed.substring(0, 255);
            lecture.setSummary(trimmed);
            log.debug("[updateLectureMetaFromPythonResponse] summary updated len={}", trimmed.length());
        }

        // keywords -> tags (255 제한)
        if (keywords != null && !keywords.isEmpty()) {
            String tagsJoined = String.join(",", keywords).trim();
            if (!tagsJoined.isBlank()) {
                if (tagsJoined.length() > 255) tagsJoined = tagsJoined.substring(0, 255);
                lecture.setTags(tagsJoined);
                log.debug("[updateLectureMetaFromPythonResponse] tags updated len={}", tagsJoined.length());
            }
        }

        lectureRepository.save(lecture);
        log.info("[updateLectureMetaFromPythonResponse] saved lectureId={}, elapsedMs={}",
                lecture.getId(), (System.currentTimeMillis() - t0));
    }

    /* ===================== 로그 보조 메서드 (마스킹·프리뷰·사이즈) ===================== */

    private String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        String user = email.substring(0, Math.min(2, at));
        String domain = email.substring(at + 1);
        String[] parts = domain.split("\\.", 2);
        String domHead = parts[0];
        String domMasked = (domHead.length() <= 2) ? "**" : domHead.substring(0, 2) + "***";
        String tail = (parts.length > 1) ? "." + parts[1] : "";
        return user + "***@" + domMasked + tail;
    }

    private String maskSession(String s) {
        if (s == null) return "null";
        return s.length() <= 6 ? "***" : s.substring(0, 3) + "***" + s.substring(s.length() - 3);
    }

    private String preview(String body, int limit) {
        if (body == null) return "null";
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    private String safeName(String name) {
        if (name == null) return "null";
        return name.length() <= 60 ? name : name.substring(0, 57) + "...";
    }

    private long safeSize(MultipartFile f) {
        try { return (f == null) ? -1L : f.getSize(); }
        catch (Exception ignore) { return -2L; }
    }

    private String shortUuid(String u) {
        if (u == null) return "null";
        return (u.length() <= 8) ? u : u.substring(0, 8);
    }
}
