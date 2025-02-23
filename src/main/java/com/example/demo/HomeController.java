package com.example.demo;

import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.LocationInfo;
import com.google.type.LatLng;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST})
public class HomeController {

    @Autowired
    private VisionService visionService;

    @Autowired
    private VisionApiFileUpload fileService;

    @GetMapping("/extract-text")
    public String extractText(@RequestParam("imageUrl") String imageUrl) {
        try {
            return visionService.extractTextFromImageUrl(imageUrl);
        } catch (Exception e) {
            return "Failed to extract text: " + e.getMessage();
        }
    }


    @PostMapping("/upload")
    public ResponseEntity<?> updateGpsData(@RequestParam("file") MultipartFile file) {
        try {
            AnnotateImageResponse imageResponse = fileService.annotateImage(file);
            List<EntityAnnotation> landmarkAnnotations = imageResponse.getLandmarkAnnotationsList();
//            List<Map<String, String>> response = new ArrayList<>();
//            for (EntityAnnotation landmarkAnnotation : landmarkAnnotations) {
//                String description = landmarkAnnotation.getDescription();
//                LocationInfo locations = landmarkAnnotation.getLocations(0);
//                LatLng latLng = locations.getLatLng();
//                String latLngStr = latLng.getLatitude() + "," + latLng.getLongitude();
//                Map<String, String> valueMap = new HashMap<>();
//                valueMap.put("description", description);
//                valueMap.put("latLngStr", latLngStr);
//                valueMap.put("score", String.valueOf(landmarkAnnotation.getScore()));
//                response.add(valueMap);
//            }

            EntityAnnotation highestScoreAnnotation = landmarkAnnotations.stream()
                    .max(Comparator.comparing(EntityAnnotation::getScore))
                    .orElse(null);

            // todo : updatedImageFile null초기화 하지 않을 수 있는 방법 찾기.
            File updatedImageFile = null;
            if (highestScoreAnnotation != null) {
                LocationInfo locations = highestScoreAnnotation.getLocations(0);
                LatLng latLng = locations.getLatLng();
                double longitude = latLng.getLongitude();
                double latitude = latLng.getLatitude();
                updatedImageFile = fileService.updateGpsData(file, longitude, latitude);
            }

            // todo updatedImageFile null exception 체크하기.
            String absolutePath = updatedImageFile.getAbsolutePath();
            Path filePath = Paths.get(absolutePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            System.out.println("absolute File Path :" + absolutePath);
            if (!resource.exists() || !resource.isReadable()) {
                throw new FileNotFoundException("File not found after upload: " + file.getOriginalFilename());
            }

            // MIME 타입 설정
            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream"; // 기본값 설정
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType)) // 원본 MIME 타입 유지
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            System.out.println("Failed to extract text: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @PostMapping("/uploads")
    public ResponseEntity<?> updateGpsDatas(@RequestParam("files") List<MultipartFile> files) {
        try {
            List<Map<String, String>> resources = new ArrayList<>();

            for (MultipartFile file : files) {
                AnnotateImageResponse imageResponse = fileService.annotateImage(file);
                List<EntityAnnotation> landmarkAnnotations = imageResponse.getLandmarkAnnotationsList();
                EntityAnnotation highestScoreAnnotation = landmarkAnnotations.stream()
                        .max(Comparator.comparing(EntityAnnotation::getScore))
                        .orElse(null);

                File updatedImageFile = null;
                if (highestScoreAnnotation != null) {
                    LocationInfo locations = highestScoreAnnotation.getLocations(0);
                    LatLng latLng = locations.getLatLng();
                    double longitude = latLng.getLongitude();
                    double latitude = latLng.getLatitude();
                    updatedImageFile = fileService.updateGpsData(file, longitude, latitude);
                }
                String absolutePath = updatedImageFile.getAbsolutePath();
//                Path filePath = Paths.get(absolutePath).normalize();
//                Resource resource = new UrlResource(filePath.toUri());

//                System.out.println("absolute File Path :" + absolutePath);
//                if (!resource.exists() || !resource.isReadable()) {
//                    throw new FileNotFoundException("File not found after upload: " + file.getOriginalFilename());
//                }
                Map<String, String> resourceKV = new HashMap<>();
                resourceKV.put("fileUrl", absolutePath); // 미리보기 URL용
                resourceKV.put("fileName", file.getOriginalFilename()); // 다운받을 파일 ID
                resources.add(resourceKV);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resources);

        } catch (Exception e) {
            System.out.println("Failed to extract text: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get("/Users/jp/Pictures/test").resolve(fileName); // 서버의 실제 파일 경로
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new FileNotFoundException("File not found " + fileName);
            }


            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (IOException e) {
            throw new RuntimeException("File not found", e);
        }
    }

    /**
     * 개별 파일 미리보기
     */
    @GetMapping("/preview/{fileName}")
    public ResponseEntity<Resource> previewFile(@PathVariable String fileName) {
        Path filePath = Paths.get("/Users/jp/Pictures/test").resolve(fileName); // 서버의 실제 파일 경로
        try {
            Resource resource = new UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }


    }
}
