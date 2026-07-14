package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.spi.BlobStorage;
import com.miniapi.router.core.config.CoreProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件 Blob 存储
 * <p>
 * 实现 {@link BlobStorage} SPI 接口，将 Prompt 和 Response 内容以文件形式存储在本地文件系统中。
 * 按照传入的相对路径在基础目录下创建文件，适合单机部署场景。
 * </p>
 */
@Component
public class LocalFileBlobStorage implements BlobStorage {

    private final Path baseDir;  // Blob 存储的基础目录路径

    /**
     * 构造函数
     * <p>
     * 从配置中读取 Blob 存储路径，并确保目录存在。
     * </p>
     *
     * @param properties 核心配置属性
     * @throws RuntimeException 当创建目录失败时抛出
     */
    public LocalFileBlobStorage(CoreProperties properties) {
        this.baseDir = Paths.get(properties.getBlobStoragePath());
        try {
            // 确保基础存储目录存在
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create blob storage dir", e);
        }
    }

    /**
     * 存储内容到本地文件
     * <p>
     * 将内容写入到基础目录下的指定相对路径，自动创建父目录。
     * </p>
     *
     * @param path    相对存储路径
     * @param content 待存储的内容
     * @return 存储路径（相对路径）
     * @throws RuntimeException 当写入文件失败时抛出
     */
    @Override
    public String store(String path, String content) {
        try {
            Path full = baseDir.resolve(path);
            // 确保父目录存在
            Files.createDirectories(full.getParent());
            Files.writeString(full, content);
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store blob: " + path, e);
        }
    }

    /**
     * 从本地文件读取内容
     *
     * @param path 相对存储路径
     * @return 文件内容字符串，若文件不存在则返回 null
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
     * 删除本地文件
     * <p>
     * 若文件存在则删除，删除失败时静默忽略。
     * </p>
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
