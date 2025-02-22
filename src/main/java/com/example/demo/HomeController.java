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

    @GetMapping("/test")
    public ResponseEntity<?> extractText() {
        try {
            AnnotateImageResponse imageResponse = fileService.test();
            List<EntityAnnotation> landmarkAnnotations = imageResponse.getLandmarkAnnotationsList();
            List<Map<String, String>> response = new ArrayList<>();
            for (EntityAnnotation landmarkAnnotation : landmarkAnnotations) {
                String description = landmarkAnnotation.getDescription();
                LocationInfo locations = landmarkAnnotation.getLocations(0);
                LatLng latLng = locations.getLatLng();
                String latLngStr = latLng.getLatitude() + "," + latLng.getLongitude();
                Map<String, String> valueMap = new HashMap<>();
                valueMap.put("description", description);
                valueMap.put("latLngStr", latLngStr);
                valueMap.put("score", String.valueOf(landmarkAnnotation.getScore()));
                response.add(valueMap);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.print("Failed to extract text: " + e.getMessage());
            e.printStackTrace();
            return null;
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

            File updatedImageFile = null;
            if (highestScoreAnnotation != null) {
                LocationInfo locations = highestScoreAnnotation.getLocations(0);
                LatLng latLng = locations.getLatLng();
                double longitude = latLng.getLongitude();
                double latitude = latLng.getLatitude();
                updatedImageFile = fileService.updateGpsData(file, longitude, latitude);
            }

            String absolutePath = updatedImageFile.getAbsolutePath();
            Path filePath = Paths.get(absolutePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            System.out.print("absolute File Path :" +absolutePath);
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
            System.out.print("Failed to extract text: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
