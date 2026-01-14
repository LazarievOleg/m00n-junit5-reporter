package com.m00nreport.reporter.model;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Represents an attachment for M00n Report.
 * 
 * <p>Supports various attachment types including screenshots, videos, and trace files.
 * Uses static factory methods for creating different attachment types.</p>
 */
public class AttachmentData {
    
    private final String id;
    private final String name;
    private final String contentType;
    private final byte[] data;
    private final File file;
    private final long size;
    
    private AttachmentData(String name, String contentType, byte[] data, File file) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.contentType = contentType;
        this.data = data;
        this.file = file;
        this.size = data != null ? data.length : (file != null ? file.length() : 0);
    }
    
    // Static factory methods
    
    /**
     * Creates attachment from byte array.
     */
    public static AttachmentData fromBytes(String name, byte[] data, String contentType) {
        return new AttachmentData(name, contentType, data, null);
    }
    
    /**
     * Creates attachment from file.
     */
    public static AttachmentData fromFile(String name, File file, String contentType) {
        return new AttachmentData(name, contentType, null, file);
    }
    
    /**
     * Creates attachment from path.
     */
    public static AttachmentData fromPath(String name, Path path, String contentType) {
        return fromFile(name, path.toFile(), contentType);
    }
    
    /**
     * Creates screenshot attachment (PNG).
     */
    public static AttachmentData screenshot(String name, byte[] data) {
        var fileName = name.endsWith(".png") ? name : name + ".png";
        return fromBytes(fileName, data, "image/png");
    }
    
    /**
     * Creates trace attachment (ZIP) from path.
     */
    public static AttachmentData trace(Path tracePath) {
        return fromPath("trace.zip", tracePath, "application/zip");
    }
    
    /**
     * Creates trace attachment (ZIP) from bytes.
     */
    public static AttachmentData trace(String name, byte[] data) {
        var fileName = name.endsWith(".zip") ? name : name + ".zip";
        return fromBytes(fileName, data, "application/zip");
    }
    
    /**
     * Creates video attachment (WebM) from path.
     */
    public static AttachmentData video(Path videoPath) {
        return fromPath("video.webm", videoPath, "video/webm");
    }
    
    /**
     * Creates video attachment (WebM) from bytes.
     */
    public static AttachmentData video(String name, byte[] data) {
        var fileName = name.endsWith(".webm") ? name : name + ".webm";
        return fromBytes(fileName, data, "video/webm");
    }
    
    // Getters
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public File getFile() { return file; }
    public long getSize() { return size; }
    
    public boolean isFileAttachment() {
        return file != null;
    }
}
