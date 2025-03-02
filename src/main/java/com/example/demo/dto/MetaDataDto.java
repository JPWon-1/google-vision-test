package com.example.demo.dto;

import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.math4.legacy.ml.clustering.DoublePoint;

import java.io.File;
import java.time.LocalDateTime;

public class MetaDataDto {
    private File imageFile;
    private LocalDateTime dateTime;
    private DoublePoint timePoint;
    private Double latitude;
    private Double longitude;

    public File getImageFile() {
        return imageFile;
    }

    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public DoublePoint getTimePoint() {
        return timePoint;
    }

    public void setTimePoint(DoublePoint timePoint) {
        this.timePoint = timePoint;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
