package com.example.demo;


import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
            File originFile = new File("/Users/jp/Pictures/test/origin_" + file.getOriginalFilename());
            file.transferTo(originFile);
            // 기존 EXIF 메타데이터 가져오기
            TiffOutputSet outputSet = null;
            try (FileInputStream fis = new FileInputStream(originFile)) {
                TiffImageMetadata metadata = (TiffImageMetadata) Imaging.getMetadata(originFile);
                if (metadata != null) {
                    outputSet = metadata.getOutputSet();
                }
            }
            // 새로운 EXIF 설정
            if (outputSet == null) {
                outputSet = new TiffOutputSet();
            }
            // GPS 디렉토리 추가
            TiffOutputDirectory gpsDir = outputSet.findDirectory(ExifTagConstants.EXIF_TAG_GPSINFO.tag);
            // 디렉토리가 없으면 새로 추가
            if (gpsDir == null) {
                TiffOutputDirectory gpsDirectory = outputSet.getOrCreateGpsDirectory();
                gpsDirectory.add(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF, (byte) 0);
                gpsDirectory.add(GpsTagConstants.GPS_TAG_GPS_ALTITUDE, RationalNumber.valueOf(0));
            }

            System.out.print("위도:" + longitude + ", 경도:" + latitude);
            // 위도, 경도 저장
            outputSet.setGpsInDegrees(longitude, latitude);

            // 새로운 파일로 저장
            File outputJpeg = new File("/Users/jp/Pictures/test/updated_" + originFile.getName());
            try (OutputStream os = new FileOutputStream(outputJpeg);
                 FileInputStream fis = new FileInputStream(originFile)) {
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
