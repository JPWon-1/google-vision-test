package com.example.demo;


import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class VisionApiFileUpload {


    public AnnotateImageResponse annotateImage(MultipartFile file) {

        // 하드코딩된 파일 경로 (분석할 이미지 파일 경로)
        AnnotateImageResponse imageResponse = null;

        try {
            // 원본 파일을 바이트 배열로 변환
            byte[] originalBytes = file.getBytes();
            ByteString imgBytes = ByteString.copyFrom(originalBytes);

            // Vision API 요청 구성
            Image image = Image.newBuilder().setContent(imgBytes).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.LANDMARK_DETECTION).build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();

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

    public File updateGpsData(MultipartFile file, double longitude, double latitude) {
        try {

            // MultipartFile → 임시 파일 변환 (저장 X)
            File tempFile = File.createTempFile(file.getOriginalFilename(), ".tmp");
            file.transferTo(tempFile);

            // 새로운 EXIF 설정
            TiffOutputSet outputSet = new TiffOutputSet();

            System.out.println("위도:" + longitude + ", 경도:" + latitude);
            // 위도, 경도 저장
            outputSet.setGpsInDegrees(longitude, latitude);

            // 새로운 파일로 저장
            File outputJpeg = new File("/Users/jp/Pictures/test/updated_" + file.getOriginalFilename());
            try (OutputStream os = new FileOutputStream(outputJpeg);
                 FileInputStream fis = new FileInputStream(tempFile)) {
                new ExifRewriter().updateExifMetadataLossless(fis, os, outputSet);
            }

            // 결과 파일을 클라이언트에 반환
            return outputJpeg;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
