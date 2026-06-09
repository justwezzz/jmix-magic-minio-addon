package org.magic.addons.minio.dto;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Request object for batch upload operations.
 * Abstracts different input sources (InputStream, Path, File) into a unified request.
 */
public class UploadRequest {

    private String objectName;
    private InputStream inputStream;
    private long size;
    private Path path;
    private File file;

    private UploadRequest() {
    }

    // ==================== Static Factory Methods ====================

    /**
     * Creates an UploadRequest from an InputStream.
     *
     * @param objectName the target object name in MinIO
     * @param inputStream the input stream to read data from
     * @param size the size of the data in bytes
     * @return a new UploadRequest
     */
    public static UploadRequest fromInputStream(String objectName, InputStream inputStream, long size) {
        UploadRequest request = new UploadRequest();
        request.objectName = objectName;
        request.inputStream = inputStream;
        request.size = size;
        return request;
    }

    /**
     * Creates an UploadRequest from a local Path with a specified object name.
     *
     * @param localPath the local file path
     * @param objectName the target object name in MinIO
     * @return a new UploadRequest
     * @throws IOException if the file cannot be read
     */
    public static UploadRequest fromPath(Path localPath, String objectName) throws IOException {
        UploadRequest request = new UploadRequest();
        request.objectName = objectName;
        request.path = localPath;
        request.size = Files.size(localPath);
        request.inputStream = Files.newInputStream(localPath);
        return request;
    }

    /**
     * Creates an UploadRequest from a local Path, using the filename as the object name.
     *
     * @param localPath the local file path
     * @return a new UploadRequest
     * @throws IOException if the file cannot be read
     */
    public static UploadRequest fromPath(Path localPath) throws IOException {
        return fromPath(localPath, localPath.getFileName().toString());
    }

    /**
     * Creates an UploadRequest from a File with a specified object name.
     *
     * @param file the file to upload
     * @param objectName the target object name in MinIO
     * @return a new UploadRequest
     * @throws IOException if the file cannot be read
     */
    public static UploadRequest fromFile(File file, String objectName) throws IOException {
        UploadRequest request = new UploadRequest();
        request.objectName = objectName;
        request.file = file;
        request.size = file.length();
        request.inputStream = new FileInputStream(file);
        return request;
    }

    /**
     * Creates an UploadRequest from a File, using the filename as the object name.
     *
     * @param file the file to upload
     * @return a new UploadRequest
     * @throws IOException if the file cannot be read
     */
    public static UploadRequest fromFile(File file) throws IOException {
        return fromFile(file, file.getName());
    }

    // ==================== Getters ====================

    public String getObjectName() {
        return objectName;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getSize() {
        return size;
    }

    public Path getPath() {
        return path;
    }

    public File getFile() {
        return file;
    }
}
