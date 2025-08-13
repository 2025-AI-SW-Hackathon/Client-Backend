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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {


    @Value("${custom.pdf.storage-dir}")
    private String storageDir; // 환경별로 경로 다르게 설정 가능

    @Value("${custom.pdf.allowed-origin}")
    private String fastapiBaseUrl;

    @Transactional
    public String saveTempPDF(MultipartFile file,Long userId) {
        try {
            // 저장할 임시 폴더 경로
            Path uploadDir = Paths.get(storageDir);

            // 폴더가 없다면 생성
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // UUID + 원래 파일명
            String originalName = file.getOriginalFilename();
            String uuid = UUID.randomUUID().toString();
            String storedFileName = uuid + "_" + originalName;
            Path filePath = uploadDir.resolve(storedFileName);

            // 파일 저장
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            User user=userRepository.findById(userId).orElseThrow(()-> new BaseException(BaseResponseStatus.USER_NOT_FOUND));

            // LectureFile 엔티티 생성
            LectureFile lectureFile = LectureFile.builder()
                    .user(user)
                    .fileName(storedFileName)
                    .fileUrl(filePath.toString()) // 또는 배포 시 URL
                    .build();

//            LectureFile lectureFile1 = lectureFileRepository.save(lectureFile);
            // 저장한 파일 ID 반환
            return storedFileName;
        } catch (IOException e) {
            throw new BaseException(BaseResponseStatus.FILE_FAIL_UPLOAD);
        }
    }

    public String sendPdfFileToFastAPI(MultipartFile file) {
        try {
            String boundary = "----SpringToFastAPI";
            HttpClient client = HttpClient.newHttpClient();

            String fileName = file.getOriginalFilename();
            String mimeType = file.getContentType();
            byte[] fileBytes = file.getBytes();

            String bodyHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: " + mimeType + "\r\n\r\n";
            String bodyFooter = "\r\n--" + boundary + "--\r\n";

            byte[] requestBody = concatenate(
                    bodyHeader.getBytes(),
                    fileBytes,
                    bodyFooter.getBytes()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fastapiBaseUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            // 동기 호출
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();

        } catch (Exception e) {
            e.printStackTrace();
            return "FastAPI 호출 실패";
        }
    }

    private byte[] concatenate(byte[]... parts) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            outputStream.write(part);
        }
        return outputStream.toByteArray();
    }


    private final UserService userService;
    private final UserRepository userRepository;
    private final LectureFileRepository lectureFileRepository;
}
