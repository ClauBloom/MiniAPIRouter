package com.miniapi.router.core.spi;

/**
 * 二进制大对象（Blob）存储接口（SPI 扩展点）。
 * <p>
 * 提供基于路径的字符串内容存储与读取能力，适用于存储提示词模板、
 * 响应内容等文本数据。具体实现可以是本地文件、OSS 或数据库。
 * </p>
 */
public interface BlobStorage {

    /** 将内容存储到指定路径，返回实际存储路径 */
    String store(String path, String content);

    /** 从指定路径读取内容 */
    String read(String path);

    /** 删除指定路径的内容 */
    void delete(String path);
}
