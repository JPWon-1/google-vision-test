package com.example.demo;


import com.example.demo.dto.MetaDataDto;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.google.type.LatLng;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.math4.legacy.ml.clustering.Cluster;
import org.apache.commons.math4.legacy.ml.clustering.DBSCANClusterer;
import org.apache.commons.math4.legacy.ml.clustering.DoublePoint;
import org.apache.commons.math4.legacy.ml.distance.EuclideanDistance;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReadMetadataService {


    public AnnotateImageResponse getAnnotateFromVisionApi(File file) {

        // 하드코딩된 파일 경로 (분석할 이미지 파일 경로)
        AnnotateImageResponse imageResponse = null;

        try {
//            // 원본 파일을 바이트 배열로 변환
//            File tempFile = File.createTempFile(file.getOriginalFilename(), ".tmp");
//            file.transferTo(tempFile);
//            // 파일에 데이터 쓰기 (테스트용)
//            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
//                fos.write("Hello World!".getBytes());
//            }

            // byte[] 변환
            byte[] originalBytes = Files.readAllBytes(file.toPath());

//            byte[] originalBytes = file.getBytes();
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

    public File updateGpsData(File file, double longitude, double latitude) {
        try {

            // 새로운 EXIF 설정
            TiffOutputSet outputSet = new TiffOutputSet();

            System.out.println("위도:" + longitude + ", 경도:" + latitude);
            // 위도, 경도 저장
            outputSet.setGpsInDegrees(longitude, latitude);

            // 새로운 파일로 저장
            File outputJpeg = new File("/Users/jp/Pictures/test/updated_" + file.getName());
            try (OutputStream os = new FileOutputStream(outputJpeg);
                 FileInputStream fis = new FileInputStream(file)) {
                new ExifRewriter().updateExifMetadataLossless(fis, os, outputSet);
            }

            // 결과 파일을 클라이언트에 반환
            return outputJpeg;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public List<List<MetaDataDto>> groupImageByDateTime(List<MultipartFile> files) {
        List<List<MetaDataDto>> clusteredDtos = new ArrayList<>();
        try {
            List<DoublePoint> points = new ArrayList<>();
            Map<DoublePoint, MetaDataDto> pointToDtoMap = new HashMap<>();
            // 1. MetaDataDto -> DoublePoint 매핑
            for (MultipartFile file : files) {
                MetaDataDto dto = readMetaData(file);
                points.add(dto.getTimePoint());
                pointToDtoMap.put(dto.getTimePoint(), dto);
            }

            // 2. DBSCAN 실행 (60초 이내의 데이터는 같은 클러스터로 묶음. 최소 단위는 0개(데이터가 없어도 클러스터 생성한다는 의미))
            DBSCANClusterer<DoublePoint> dbscan = new DBSCANClusterer<>(60, 0, new EuclideanDistance());
            List<Cluster<DoublePoint>> clusterResults = dbscan.cluster(points);

            // 3. 클러스터 결과를 MetaDataDto 객체별로 묶기
            for (Cluster<DoublePoint> cluster : clusterResults) {
                List<MetaDataDto> clusterGroup = new ArrayList<>();
                for (DoublePoint point : cluster.getPoints()) {
                    clusterGroup.add(pointToDtoMap.get(point));
                }
                clusteredDtos.add(clusterGroup);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return clusteredDtos;
    }

    public MetaDataDto readMetaData(MultipartFile file) {
        MetaDataDto metaDataDto = new MetaDataDto();
        try {
            // MultipartFile → 임시 파일 변환 (저장 X)
            File tempFile = File.createTempFile(file.getOriginalFilename(), ".tmp");
            file.transferTo(tempFile);
            ImageMetadata metadata = Imaging.getMetadata(tempFile);
            metaDataDto.setImageFile(tempFile);
            if (metadata instanceof JpegImageMetadata) {
                final JpegImageMetadata jpegMetaData = (JpegImageMetadata) metadata;

                // 사진 촬영된 시간
                TiffField tiffDateTime = jpegMetaData.findExifValueWithExactMatch(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                LocalDateTime time = LocalDateTime.MIN;
                if (tiffDateTime != null) {
                    String dateTimeString = tiffDateTime.getStringValue();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
                    time = LocalDateTime.parse(dateTimeString, formatter);
                }
                metaDataDto.setDateTime(time);
                long epochSecond = time.toEpochSecond(ZoneOffset.UTC);
                DoublePoint timePoint = new DoublePoint(new double[]{epochSecond});
                metaDataDto.setTimePoint(timePoint);

                // 위,경도
                TiffField latitude = jpegMetaData.findExifValueWithExactMatch(GpsTagConstants.GPS_TAG_GPS_LATITUDE);
                String latitudeRef = jpegMetaData.findExifValueWithExactMatch(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF).getStringValue();
                TiffField longitude = jpegMetaData.findExifValueWithExactMatch(GpsTagConstants.GPS_TAG_GPS_LONGITUDE);
                String longitudeRef = jpegMetaData.findExifValueWithExactMatch(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF).getStringValue();
                Double lat = null;
                Double lon = null;
                if (latitude != null) {
                    double[] latDoubleArray = latitude.getDoubleArrayValue();
                    double latDegree = latDoubleArray[0];
                    double latMinute = latDoubleArray[1];
                    double latSecond = latDoubleArray[2];
                    lat = latDegree + (latMinute / 60) + (latSecond / 3600);
                    if ("S".equals(latitudeRef)) {
                        lat = lat * -1;
                    }
                }

                if (longitude != null) {
                    double[] lonDoubleArray = longitude.getDoubleArrayValue();
                    double lonDegree = lonDoubleArray[0];
                    double lonMinute = lonDoubleArray[1];
                    double lonSecond = lonDoubleArray[2];
                    lon = lonDegree + (lonMinute / 60) + (lonSecond / 3600);
                    if ("W".equals(longitudeRef)) {
                        lon = lon * -1;
                    }
                }
                metaDataDto.setLatitude(lat);
                metaDataDto.setLongitude(lon);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaDataDto;
    }

    public void setGpsInfo(List<MetaDataDto> metaDataList) {
        // 1. GPS 정보가 있는 객체 필터링
        List<MetaDataDto> gpsList = metaDataList.stream()
                .filter(dto -> dto.getLatitude() != null && dto.getLongitude() != null)
                .collect(Collectors.toList());

        Boolean isValid = false;

        if (!gpsList.isEmpty()) {
            isValid = true;
            // 2. GPS가 있는 객체가 하나라도 있으면 나머지 객체에 동일 GPS 채우기
            double lat = gpsList.get(0).getLatitude();
            double lon = gpsList.get(0).getLongitude();

            for (MetaDataDto dto : metaDataList) {
                if (dto.getLatitude() == null || dto.getLongitude() == null) {
                    File file = this.updateGpsData(dto.getImageFile(), lon, lat);
                    dto.setImageFile(file);
                    dto.setLatitude(lat);
                    dto.setLongitude(lon);
                }
            }
        } else {
            Double lon = null;
            Double lat = null;
            // 3. GPS 정보가 전혀 없으면 Vision API 호출
            for (MetaDataDto dto : metaDataList) {
                AnnotateImageResponse annotation = this.getAnnotateFromVisionApi(dto.getImageFile());
                if (annotation != null) {
                    List<EntityAnnotation> landmarkAnnotations = annotation.getLandmarkAnnotationsList();
                    EntityAnnotation highestScoreAnnotation = landmarkAnnotations.stream()
                            .max(Comparator.comparing(EntityAnnotation::getScore))
                            .orElse(null);
                    if (highestScoreAnnotation != null) {
                        LocationInfo locations = highestScoreAnnotation.getLocations(0);
                        LatLng latLng = locations.getLatLng();
                        double latitude = latLng.getLatitude();
                        double longitude = latLng.getLongitude();
                        File file = this.updateGpsData(dto.getImageFile(), longitude, latitude);
                        dto.setLatitude(latitude);
                        dto.setLongitude(longitude);
                        dto.setImageFile(file);
                        lon = longitude;
                        lat = latitude;
                        isValid = true;
                        break;
                    }
                }
            }
            if (isValid) {
                for (MetaDataDto dto : metaDataList) {
                    if (dto.getLatitude() == null || dto.getLongitude() == null) {
                        File file = this.updateGpsData(dto.getImageFile(), lon, lat);
                        dto.setImageFile(file);
                        dto.setLatitude(lat);
                        dto.setLongitude(lon);
                    }
                }
            }
        }
    }

}
