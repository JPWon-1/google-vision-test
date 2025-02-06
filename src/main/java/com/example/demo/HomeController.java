package com.example.demo;

import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.LocationInfo;
import com.google.type.LatLng;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
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
}
