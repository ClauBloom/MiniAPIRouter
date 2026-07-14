package com.miniapi.router.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON 序列化/反序列化工具类。
 * <p>
 * 基于 Jackson 提供统一的 JSON 处理能力，全局配置为：
 * 序列化时忽略 null 值、使用 snake_case 命名策略、忽略未知属性、
 * 日期以字符串形式输出。
 * </p>
 */
public final class JsonUtils {

    /**
     * 全局共享的 ObjectMapper 实例。
     * <p>
     * 配置说明：
     * 1. 忽略 null 值，减少 JSON 体积
     * 2. 驼峰 ↔ snake_case 自动转换
     * 3. 反序列化时忽略未知 JSON 字段
     * 4. 日期以 ISO 字符串形式输出而非时间戳
     * </p>
     */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .findAndRegisterModules();

    private JsonUtils() {}

    /**
     * 将 Java 对象序列化为 JSON 字符串。
     *
     * @param obj 待序列化的对象
     * @return JSON 字符串
     * @throws RuntimeException 序列化失败时抛出
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("toJson failed", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的 Java 对象。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 反序列化后的对象
     * @throws RuntimeException 反序列化失败时抛出
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("fromJson failed", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为泛型类型的 Java 对象。
     *
     * @param json    JSON 字符串
     * @param typeRef 泛型类型引用，如 {@code new TypeReference<List<User>>() {}}
     * @param <T>     目标泛型
     * @return 反序列化后的对象
     * @throws RuntimeException 反序列化失败时抛出
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("fromJson failed", e);
        }
    }

    /**
     * 将 JSON 字符串解析为 JsonNode 树。
     *
     * @param json JSON 字符串
     * @return JsonNode 根节点
     * @throws RuntimeException 解析失败时抛出
     */
    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("parse failed", e);
        }
    }

    /**
     * 将 JSON 字节数组解析为 JsonNode 树。
     *
     * @param json JSON 字节数组
     * @return JsonNode 根节点
     * @throws RuntimeException 解析失败时抛出
     */
    public static JsonNode parseTree(byte[] json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("parse failed", e);
        }
    }
}
