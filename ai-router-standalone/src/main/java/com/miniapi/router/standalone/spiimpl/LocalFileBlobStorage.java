package com.miniapi.router.standalone.spiimpl;

import com.miniapi.router.core.config.CoreProperties;
import com.miniapi.router.core.spi.BlobStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件 Blob 存储实现。
 * <p>
 * 实现 BlobStorage SPI 接口，将 Prompt 和 Response 内容存储到本地文件系统。
 * 存储路径基于配置的 blobStoragePath，按相对路径组织文件。
 * </p>
 */
@Component
public class LocalFileBlobStorage implements BlobStorage {

    private final Path baseDir; // Blob 存储基础目录

    public LocalFileBlobStorage(CoreProperties properties) {
        this.baseDir = Paths.get(properties.getBlobStoragePath());
        try {
            Files.createDirectories(baseDir); // 确保存储目录存在
        } catch (IOException e) {
            throw new RuntimeException("Failed to create blob storage dir", e);
        }
    }

    /**
     * 存储内容到本地文件。
     *
     * @param path    相对存储路径
     * @param content 文件内容
     * @return 存储路径（相对路径作为 URL）
     */
    @Override
    public String store(String path, String content) {
        try {
            Path full = baseDir.resolve(path);
            Files.createDirectories(full.getParent()); // 确保父目录存在
            Files.writeString(full, content);
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store blob: " + path, e);
        }
    }

    /**
     * 从本地文件读取内容。
     *
     * @param path 相对存储路径
     * @return 文件内容字符串，文件不存在或读取失败返回 null
     */
    @Override
    public String read(String path) {
        try {
            Path full = baseDir.resolve(path);
            if (!Files.exists(full)) return null;
            return Files.readString(full);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 删除本地文件。
     *
     * @param path 相对存储路径
     */
    @Override
    public void delete(String path) {
        try {
            Path full = baseDir.resolve(path);
            Files.deleteIfExists(full);
        } catch (IOException ignored) {
        }
    }
}
