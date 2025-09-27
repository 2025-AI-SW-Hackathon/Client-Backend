package org.example.speaknotebackend.controller;


import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.config.UserDetailsImpl;
import org.example.speaknotebackend.domain.repository.LectureFileRepository;
import org.example.speaknotebackend.domain.repository.LectureRepository;
import org.example.speaknotebackend.dto.AnnotationVersionListResponse;
import org.example.speaknotebackend.dto.FileAnnotationResponse;
import org.example.speaknotebackend.entity.Lecture;
import org.example.speaknotebackend.entity.LectureFile;
import org.example.speaknotebackend.service.FileAnnotationQueryService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping(value = "/api/files", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class FileAnnotationQueryController {

    private final FileAnnotationQueryService service;
    private final LectureFileRepository lectureFileRepository;

    /** 최신 또는 특정 버전 주석 + 파일 메타 조회 */
    @GetMapping("/{fileId}/annotations")
    public FileAnnotationResponse getFileAnnotations(
            @PathVariable Long fileId,
            @RequestParam(required = false) Integer version,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        return service.getFileWithAnnotations(fileId, user.getUserId(), version);
    }

    /** 버전 목록 조회 (최근 → 과거) */
    @GetMapping("/{fileId}/annotation-versions")
    public AnnotationVersionListResponse listAnnotationVersions(
            @PathVariable Long fileId,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        return service.listVersions(fileId, user.getUserId());
    }


    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> getFileContent(
            @PathVariable Long fileId,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // 1) 파일/강의 조회
        LectureFile file = lectureFileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 메타데이터 없음"));

        Lecture lecture = lectureRepository.findByLectureFile_Id(fileId);
        if (lecture == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일에 연결된 강의를 찾을 수 없습니다.");
        }
        if (!Objects.equals(lecture.getUser().getId(), user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // 2) 파일명/콘텐츠타입
        String downloadName = (file.getFileName() != null && !file.getFileName().isBlank())
                ? file.getFileName()
                : ("file-" + file.getId() + ".pdf");

        MediaType mediaType = MediaType.APPLICATION_PDF; // PDF만 제공한다면 고정, 아니면 탐지 로직 추가

        // 3) 로컬 vs GCS 분기
        String fileUrl = file.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 경로가 비어있습니다.");
        }

        try {
            if (fileUrl.startsWith("gs://")) {
                // ---------- GCS ----------
                GcsRef ref = parseGsUrl(fileUrl, file.getUuid(), file.getFileName()); // 아래 헬퍼
                Storage storage = StorageOptions.getDefaultInstance().getService();
                Blob blob = storage.get(ref.bucket(), ref.object()); // 권한: VM SA / ADC

                if (blob == null || !blob.exists()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GCS 객체를 찾을 수 없습니다.");
                }

                // 스트림으로 바디 전달
                ReadChannel reader = blob.reader(); // close 필요
                InputStream is = Channels.newInputStream(reader);
                InputStreamResource body = new InputStreamResource(is) {
                    @Override public long contentLength() { return blob.getSize(); }
                    @Override public String getFilename() { return downloadName; }
                };

                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .contentLength(blob.getSize())
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionInline(downloadName))
                        .eTag(blob.getEtag())
                        .lastModified(Optional.ofNullable(blob.getUpdateTime()).orElse(0L))
                        .body(body);

            } else {
                // ---------- Local ----------
                // fileUrl: 디렉터리 경로, 실제 저장명: uuid_originalName
                Path dir = Paths.get(fileUrl).toAbsolutePath().normalize();
                String safeName = (file.getFileName() == null ? "uploaded.pdf"
                        : file.getFileName().replaceAll("[\\\\/\\r\\n\\t]", "_"));
                String stored = file.getUuid() + "_" + safeName;
                Path path = dir.resolve(stored).normalize();

                if (!path.startsWith(dir) || !Files.exists(path)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "로컬 파일을 찾을 수 없습니다.");
                }

                long size = Files.size(path);
                InputStream is = Files.newInputStream(path);
                InputStreamResource body = new InputStreamResource(is) {
                    @Override public long contentLength() { return size; }
                    @Override public String getFilename() { return downloadName; }
                };

                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .contentLength(size)
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionInline(downloadName))
                        .body(body);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 읽기 실패");
        }
    }

    private static String contentDispositionInline(String filename) {
        // RFC 5987: filename* 로 UTF-8 안전 전송
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return "inline; filename=\"" + filename.replace("\"","") + "\"; filename*=UTF-8''" + encoded;
    }

    private record GcsRef(String bucket, String object) {}
    private static GcsRef parseGsUrl(String fileUrl, String uuid, String originalName) {
        // fileUrl: "gs://bucket/prefix/..." 일 수도 있고, 폴더 URL만 저장했으면 uuid_original 조합
        if (!fileUrl.startsWith("gs://")) throw new IllegalArgumentException("not gs url");
        String noScheme = fileUrl.substring(5); // skip "gs://"
        int slash = noScheme.indexOf('/');
        String bucket = (slash < 0) ? noScheme : noScheme.substring(0, slash);
        String object = (slash < 0) ? "" : noScheme.substring(slash + 1);

        if (object == null || object.isBlank()) {
            // DB에 폴더만 저장된 경우: objectName 재조합
            String safeName = (originalName == null ? "uploaded.pdf"
                    : originalName.replaceAll("[\\\\/\\r\\n\\t]", "_"));
            object = uuid + "_" + safeName;
        }
        return new GcsRef(bucket, object);
    }

    private final LectureRepository lectureRepository;
}
