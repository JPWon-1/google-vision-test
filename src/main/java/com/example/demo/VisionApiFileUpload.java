package com.example.demo;


import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class VisionApiFileUpload {

    public AnnotateImageResponse test() {
        // 하드코딩된 파일 경로 (분석할 이미지 파일 경로)
        String filePath = "/Users/jp/workspace/visionApi/demo/src/main/java/com/example/demo/test3.png"; // 이 경로를 분석할 이미지 파일 경로로 변경하세요.
        AnnotateImageResponse imageResponse = null;
        try {
            // 로컬 파일 읽기
            ByteString imgBytes = ByteString.copyFrom(Files.readAllBytes(Paths.get(filePath)));

            // Vision API 요청 구성
            Image image = Image.newBuilder().setContent(imgBytes).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.LANDMARK_DETECTION).build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            // Vision API 클라이언트 생성 및 요청 실행
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                List<AnnotateImageResponse> responses = client.batchAnnotateImages(requests).getResponsesList();

                for (AnnotateImageResponse response : responses) {
                    if (response.hasError()) {
                        System.out.printf("Error: %s%n", response.getError().getMessage());
                        return response;
                    }

                    List<EntityAnnotation> landmarkAnnotationsList = response.getLandmarkAnnotationsList();
                    for (EntityAnnotation entityAnnotation : landmarkAnnotationsList) {
                        // 라벨 출력
                        System.out.println("이미지 분석 결과:");
                        System.out.printf("Label: %s (Score: %.2f)%n", entityAnnotation.getDescription(), entityAnnotation.getScore());

                    }
                }
                imageResponse = responses.get(0);
            }
        } catch (Exception e) {
            System.out.println("이미지 파일 읽기 또는 분석 중 오류가 발생했습니다.");
            e.printStackTrace();
        }

        return imageResponse;
    }
}
