package cn.realm.cloud.framework.common.util.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Jackson JSON 工具类（线程安全，不可变）。
 * <p>
 * 提供高效、安全的 JSON 序列化与反序列化方法，支持泛型、集合以及复杂嵌套类型。
 * 所有方法均采用统一的异常体系 {@link JacksonException}，将受检异常转换为运行时异常，
 * 避免调用方强制捕获。同时提供安全方法（后缀 OrNull）在异常时返回 {@code null} 并记录日志。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 序列化
 * String json = JsonUtils.toJsonString(user);
 * byte[] bytes = JsonUtils.toJsonBytes(user);
 *
 * // 反序列化
 * User user = JsonUtils.parseObject(json, User.class);
 * List<User> users = JsonUtils.parseArray(json, User.class);
 *
 * // 泛型复杂类型
 * Map<String, List<User>> map = JsonUtils.parseObject(json,
 *         new TypeReference<Map<String, List<User>>>() {});
 *
 * // 对象转换
 * UserDTO dto = JsonUtils.convertValue(user, UserDTO.class);
 * }</pre>
 *
 * @author QI Guang
 * @version 1.0.0
 */
public final class JsonUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

    /**
     * 全局共享的 ObjectMapper 实例，配置完成后不可变，线程安全。
     */
    private static final ObjectMapper OBJECT_MAPPER = createDefaultObjectMapper();

    /**
     * JavaType 缓存，用于提升复杂泛型类型的解析性能。
     * 使用 ConcurrentHashMap 保证线程安全，键为 Type，值为对应的 JavaType。
     * 注意：如果应用频繁使用动态生成的匿名 Type（如匿名内部类），可能导致缓存膨胀，
     * 建议在必要时调用 {@link #clearTypeCache()} 清理。
     */
    private static final ConcurrentMap<Type, JavaType> TYPE_CACHE = new ConcurrentHashMap<>(64);

    private JsonUtils() {
        throw new IllegalStateException("JsonUtils 工具类不允许实例化");
    }

    // ======================== 初始化配置 ========================

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ----- 反序列化配置 -----
        // 忽略 JSON 中存在而 Java 对象没有的字段
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 允许使用单引号（非标准，视场景开启，默认关闭）
        // mapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        // 允许使用注释（调试友好）
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);

        // ----- 序列化配置 -----
        // 序列化空 Bean 时不报错
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 日期时间不输出为时间戳，而是格式化的字符串（配合 JavaTimeModule）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 只包含非 null 属性，减少传输体积
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // ----- 其他全局配置 -----
        // 统一时区为 UTC，避免不同环境日期解析不一致
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        // 注册 Java 8 时间模块（LocalDate, LocalDateTime 等）
        mapper.registerModule(new JavaTimeModule());

        return mapper;
    }

    // ======================== 公共访问 ========================

    /**
     * 获取全局共享的 ObjectMapper 实例（已配置完整）。
     * 注意：返回的实例是线程安全的，但对其的任何修改（如配置变更）将影响全局。
     * 如需独立配置，请使用 {@link #copyObjectMapper()}。
     *
     * @return 全局 ObjectMapper
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * 复制一份与全局配置完全相同的 ObjectMapper 实例。
     * 适用于需要临时定制配置（如开启/关闭特定特性）且不影响全局的场景。
     *
     * @return 独立的 ObjectMapper 副本
     */
    public static ObjectMapper copyObjectMapper() {
        return OBJECT_MAPPER.copy();
    }

    /**
     * 清理类型缓存，释放内存。
     * 当应用中动态生成了大量 Type 对象时，可定期调用此方法防止缓存膨胀。
     */
    public static void clearTypeCache() {
        TYPE_CACHE.clear();
        LOGGER.debug("Jackson Type cache cleared.");
    }

    // ======================== 序列化（对象 → JSON） ========================

    /**
     * 将对象序列化为紧凑格式的 JSON 字符串。
     *
     * @param obj 待序列化对象（可为 null，此时序列化结果为 "null"）
     * @return JSON 字符串
     * @throws JacksonException 序列化失败时抛出
     */
    public static String toJsonString(Object obj) throws JacksonException {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to serialize object to JSON string", e);
        }
    }

    /**
     * 安全版本：将对象序列化为 JSON 字符串，异常时返回 {@code null} 并记录警告日志。
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，失败返回 {@code null}
     */
    public static String toJsonStringOrNull(Object obj) {
        try {
            return toJsonString(obj);
        } catch (JacksonException e) {
            LOGGER.warn("Failed to serialize object to JSON, returning null. obj: {}", obj, e);
            return null;
        }
    }

    /**
     * 将对象序列化为格式化的（缩进）JSON 字符串，便于调试。
     *
     * @param obj 待序列化对象
     * @return 格式化后的 JSON 字符串
     * @throws JacksonException 序列化失败时抛出
     */
    public static String toPrettyJsonString(Object obj) throws JacksonException {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to serialize object to pretty JSON string", e);
        }
    }

    /**
     * 安全版本：格式化序列化，异常时返回 {@code null} 并记录警告日志。
     *
     * @param obj 待序列化对象
     * @return 格式化 JSON 字符串，失败返回 {@code null}
     */
    public static String toPrettyJsonStringOrNull(Object obj) {
        try {
            return toPrettyJsonString(obj);
        } catch (JacksonException e) {
            LOGGER.warn("Failed to serialize object to pretty JSON, returning null. obj: {}", obj, e);
            return null;
        }
    }

    /**
     * 将对象序列化为字节数组。
     *
     * @param obj 待序列化对象
     * @return JSON 字节数组
     * @throws JacksonException 序列化失败时抛出
     */
    public static byte[] toJsonBytes(Object obj) throws JacksonException {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to serialize object to JSON bytes", e);
        }
    }

    /**
     * 将对象序列化并写入输出流。
     *
     * @param out 输出流（不会自动关闭）
     * @param obj 待序列化对象
     * @throws JacksonException 序列化或 I/O 错误时抛出
     */
    public static void writeValue(OutputStream out, Object obj) throws JacksonException {
        try {
            OBJECT_MAPPER.writeValue(out, obj);
        } catch (IOException e) {
            throw new JacksonException("Failed to write JSON to OutputStream", e);
        }
    }

    // ======================== 反序列化（JSON → 对象） ========================

    /**
     * 将 JSON 字符串解析为指定类型的对象。
     *
     * @param json  JSON 字符串（不能为 null 或空）
     * @param clazz 目标类型 Class
     * @param <T>   目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析失败或输入为空时抛出
     */
    public static <T> T parseObject(String json, Class<T> clazz) throws JacksonException {
        if (json == null || json.isEmpty()) {
            throw new JacksonException("JSON string is null or empty");
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to parse JSON: " + json, e);
        }
    }

    /**
     * 安全版本：解析 JSON，异常时返回 {@code null}。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 解析后的对象，失败返回 {@code null}
     */
    public static <T> T parseObjectOrNull(String json, Class<T> clazz) {
        try {
            return parseObject(json, clazz);
        } catch (JacksonException e) {
            LOGGER.warn("Failed to parse JSON to {}, returning null. json: {}", clazz.getSimpleName(), json, e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为复杂类型（支持泛型、集合等）。
     * <p>示例：{@code Map<String, List<User>> map = parseObject(json, new TypeReference<Map<String, List<User>>>() {});}</p>
     *
     * @param json          JSON 字符串
     * @param typeReference 类型引用
     * @param <T>           目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析失败时抛出
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) throws JacksonException {
        if (json == null || json.isEmpty()) {
            throw new JacksonException("JSON string is null or empty");
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to parse JSON with TypeReference: " + json, e);
        }
    }

    /**
     * 将 JSON 字符串解析为指定 Java {@link Type} 的对象。
     * 适用于无法使用 Class 或 TypeReference 的场景（如 Spring 的 ParameterizedTypeReference）。
     *
     * @param json JSON 字符串
     * @param type Java Type
     * @param <T>  目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析失败时抛出
     */
    public static <T> T parseObject(String json, Type type) throws JacksonException {
        if (json == null || json.isEmpty()) {
            throw new JacksonException("JSON string is null or empty");
        }
        try {
            JavaType javaType = getCachedJavaType(type);
            return OBJECT_MAPPER.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to parse JSON with Type: " + json, e);
        }
    }

    /**
     * 从输入流解析 JSON 为指定类型对象。
     *
     * @param inputStream 输入流（不会自动关闭）
     * @param clazz       目标类型
     * @param <T>         目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析或 I/O 错误时抛出
     */
    public static <T> T parseObject(InputStream inputStream, Class<T> clazz) throws JacksonException {
        try {
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (IOException e) {
            throw new JacksonException("Failed to parse InputStream to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * 从输入流解析 JSON 为复杂类型对象（支持泛型）。
     *
     * @param inputStream   输入流
     * @param typeReference 类型引用
     * @param <T>           目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析或 I/O 错误时抛出
     */
    public static <T> T parseObject(InputStream inputStream, TypeReference<T> typeReference) throws JacksonException {
        try {
            return OBJECT_MAPPER.readValue(inputStream, typeReference);
        } catch (IOException e) {
            throw new JacksonException("Failed to parse InputStream with TypeReference", e);
        }
    }

    /**
     * 从字节数组解析 JSON 为指定类型对象。
     *
     * @param bytes JSON 字节数组
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析失败时抛出
     */
    public static <T> T parseObject(byte[] bytes, Class<T> clazz) throws JacksonException {
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new JacksonException("Failed to parse byte[] to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * 从字节数组解析 JSON 为复杂类型对象。
     *
     * @param bytes         JSON 字节数组
     * @param typeReference 类型引用
     * @param <T>           目标类型
     * @return 解析后的对象
     * @throws JacksonException 解析失败时抛出
     */
    public static <T> T parseObject(byte[] bytes, TypeReference<T> typeReference) throws JacksonException {
        try {
            return OBJECT_MAPPER.readValue(bytes, typeReference);
        } catch (IOException e) {
            throw new JacksonException("Failed to parse byte[] with TypeReference", e);
        }
    }

    /**
     * 将 JSON 数组字符串解析为 {@link List} 集合。
     * <p>示例：{@code List<User> users = parseArray(json, User.class);}</p>
     *
     * @param json  JSON 数组字符串
     * @param clazz 集合元素类型
     * @param <T>   元素类型
     * @return 解析后的 List
     * @throws JacksonException 解析失败时抛出
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) throws JacksonException {
        if (json == null || json.isEmpty()) {
            throw new JacksonException("JSON string is null or empty");
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to parse JSON array: " + json, e);
        }
    }

    /**
     * 安全版本：解析 JSON 数组为 List，异常时返回 {@code null}。
     *
     * @param json  JSON 数组字符串
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 解析后的 List，失败返回 {@code null}
     */
    public static <T> List<T> parseArrayOrNull(String json, Class<T> clazz) {
        try {
            return parseArray(json, clazz);
        } catch (JacksonException e) {
            LOGGER.warn("Failed to parse JSON array to List<{}>, returning null. json: {}", clazz.getSimpleName(), json, e);
            return null;
        }
    }

    // ======================== JSON 树模型操作 ========================

    /**
     * 将 JSON 字符串解析为 {@link JsonNode} 树模型，用于动态操作。
     *
     * @param json JSON 字符串
     * @return JsonNode 根节点
     * @throws JacksonException 解析失败时抛出
     */
    public static JsonNode readTree(String json) throws JacksonException {
        if (json == null || json.isEmpty()) {
            throw new JacksonException("JSON string is null or empty");
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to parse JSON to JsonNode: " + json, e);
        }
    }

    /**
     * 创建一个空的 {@link ObjectNode}，用于构建 JSON 对象。
     *
     * @return 空的 ObjectNode
     */
    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    // ======================== 对象转换 ========================

    /**
     * 将已知对象转换为指定类型的对象（基于类型转换）。
     * <p>示例：{@code UserDTO dto = convertValue(userEntity, UserDTO.class);}</p>
     *
     * @param fromValue   源对象
     * @param toValueType 目标类型 Class
     * @param <T>         目标类型
     * @return 转换后的对象
     * @throws JacksonException 转换失败时抛出
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) throws JacksonException {
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new JacksonException("Failed to convert value to " + toValueType.getName(), e);
        }
    }

    /**
     * 将已知对象转换为复杂类型对象（支持泛型）。
     *
     * @param fromValue   源对象
     * @param toValueType 类型引用
     * @param <T>         目标类型
     * @return 转换后的对象
     * @throws JacksonException 转换失败时抛出
     */
    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueType) throws JacksonException {
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new JacksonException("Failed to convert value with TypeReference", e);
        }
    }

    // ======================== 内部工具方法 ========================

    /**
     * 从缓存中获取或构建 JavaType，减少重复构造开销。
     *
     * @param type Java Type
     * @return 对应的 JavaType
     */
    private static JavaType getCachedJavaType(Type type) {
        return TYPE_CACHE.computeIfAbsent(type, t -> OBJECT_MAPPER.getTypeFactory().constructType(t));
    }

    // ======================== 自定义异常 ========================

    /**
     * Jackson 运行时异常，将受检异常包装为非受检异常。
     */
    public static class JacksonException extends RuntimeException {
        public JacksonException(String message) {
            super(message);
        }

        public JacksonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
