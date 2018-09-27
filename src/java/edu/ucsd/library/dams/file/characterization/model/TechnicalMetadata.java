package edu.ucsd.library.dams.file.characterization.model;

import java.io.Serializable;
import java.util.Date;

import org.json.simple.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The model for technical metadata
 * @author lsitu
 */
public class TechnicalMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fitsVersion = null;

    private String exifVersion = null;

    private String fileName = null;

    private String filePath = null;

    private long size = 0L;

    private Date dateCreated = null;

    private Date dateModified = null;

    private String mimeType = null;

    private String format = null;

    private String version = null;      // format version

    private String duration = null;     // video/audio duration
    
    private String quality = null;      // quality for image, video, audio

    private boolean wellFormed = true; // format well-formed?

    private boolean valid = true;      // format valid?

    private String status = null;      // format status message

    private String checksumCRC32 = null;

    private String checksumMD5 = null;

    private String checksumSHA = null;

    private String imageProducer = null;
    
    private String captureDevice = null;
    
    private String scanningSoftware = null;

    public String toString() {
        JSONObject data = new JSONObject();
        for (Field f : getClass().getDeclaredFields()) {
            try {
                if (!Modifier.isStatic(f.getModifiers())) {
                    data.put(f.getName(), f.get(this));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return data.toJSONString();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateModified() {
        return dateModified;
    }

    public void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    
    public String getChecksumCRC32() {
        return checksumCRC32;
    }

    public void setChecksumCRC32(String checksumCRC32) {
        this.checksumCRC32 = checksumCRC32;
    }

    public String getChecksumMD5() {
        return checksumMD5;
    }

    public void setChecksumMD5(String checksumMD5) {
        this.checksumMD5 = checksumMD5;
    }

    public String getChecksumSHA() {
        return checksumSHA;
    }

    public void setChecksumSHA(String checksumSHA) {
        this.checksumSHA = checksumSHA;
    }

    public boolean getWellFormed() {
        return wellFormed;
    }

    public void setWellFormed(boolean wellFormed) {
        this.wellFormed = wellFormed;
    }

    public boolean getValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

   public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFitsVersion() {
        return fitsVersion;
    }

    public void setFitsVersion(String fitsVersion) {
        this.fitsVersion = fitsVersion;
    }

   public String getExifVersion() {
        return exifVersion;
    }

    public void setExifVersion(String exifVersion) {
        this.exifVersion = exifVersion;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getImageProducer() {
        return imageProducer;
    }

    public void setImageProducer(String imageProducer) {
        this.imageProducer = imageProducer;
    }

    public String getCaptureDevice() {
        return captureDevice;
    }

    public void setCaptureDevice(String captureDevice) {
        this.captureDevice = captureDevice;
    }

    public String getScanningSoftware() {
        return scanningSoftware;
    }

    public void setScanningSoftware(String scanningSoftware) {
        this.scanningSoftware = scanningSoftware;
    }
}
