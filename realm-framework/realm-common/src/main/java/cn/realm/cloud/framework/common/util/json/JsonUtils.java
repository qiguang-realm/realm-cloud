package cn.realm.cloud.framework.common.util.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.lang.reflect.Type;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Jackson JSON 工具类（线程安全）。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>序列化：{@link #toJsonString(Object)}、{@link #toJsonBytes(Object)}、{@link #writeValue(OutputStream, Object)}</li>
 *     <li>反序列化：{@link #parseObject(String, Class)}、{@link #parseArray(String, Class)}、{@link #parseObject(String, Type)}</li>
 *     <li>JSON 树模型：{@link #readTree(String)}、{@link #createObjectNode()}</li>
 *     <li>对象转换：{@link #convertValue(Object, Class)}</li>
 *     <li>安全版本（后缀 {@code OrNull}）：异常时返回 {@code null} 并记录警告日志</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类所有方法均为线程安全。{@link #getObjectMapper()} 返回的实例
 * 可被多处共享；如需定制配置，请使用 {@link #copyObjectMapper()} 获取独立副本。
 *
 * <p><b>非标准 JSON 支持：</b>默认允许 JSON 中包含注释（{@code //} 与 {@code /*}），
 * 这是非标准特性，便于调试。如需严格标准 JSON 解析，请使用 {@link #copyObjectMapper()}
 * 后自行调整配置。
 *
 * <p><b>敏感数据：</b>异常消息与日志中<b>只包含 JSON 前 200 字符</b>，
 * 避免大 JSON 撑爆日志或泄露敏感数据（CWE-209 / CWE-532）。
 *
 * <p><b>使用示例：</b>
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
 * @since 1.0.0
 */
public final class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    /**
     * 异常消息与日志中 JSON 的最大展示长度。
     * <p>防止大 JSON 撑爆日志或泄露敏感数据。
     */
    private static final int JSON_ABBREVIATE_LENGTH = 200;

    /**
     * 类型缓存的最大容量。
     * <p>通过 Guava Cache 限制容量，防止动态类型导致 OOM。
     */
    private static final int TYPE_CACHE_MAX_SIZE = 10_000;

    /**
     * 全局共享的 {@link ObjectMapper} 实例，配置完成后不可变，线程安全。
     */
    private static final ObjectMapper OBJECT_MAPPER = createDefaultObjectMapper();

    /**
     * {@link JavaType} 缓存，用于提升复杂泛型类型的解析性能。
     * <p>使用 Guava Cache 限制最大容量，防止动态类型无限增长导致 OOM。
     */
    private static final Cache<Type, JavaType> TYPE_CACHE = CacheBuilder.newBuilder()
            .maximumSize(TYPE_CACHE_MAX_SIZE)
            .build();

    private JsonUtils() {
        throw new UnsupportedOperationException(
                "JsonUtils is a static utility class and cannot be instantiated");
    }

    // ======================== 初始化配置 ========================

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ----- 反序列化配置 -----
        // 忽略 JSON 中存在而 Java 对象没有的字段
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 允许 JSON 中包含注释（非标准特性，便于调试）
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);

        // ----- 序列化配置 -----
        // 序列化空 Bean 时不报错
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 日期时间不输出为时间戳，而是格式化的字符串（配合 JavaTimeModule）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 只包含非 null 属性，减少传输体积
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // ----- 时区配置 -----
        // 统一时区为 UTC，避免不同环境日期解析不一致
        mapper.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

        // ----- Java 8 时间模块 -----
        mapper.registerModule(new JavaTimeModule());

        return mapper;
    }

    // ======================== 公共访问 ========================

    /**
     * 获取全局共享的 {@link ObjectMapper} 实例。
     *
     * <p><b>注意：</b>返回的实例线程安全，但对其的任何配置修改会影响全局。
     * 如需独立配置，请使用 {@link #copyObjectMapper()}。
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * 复制一份与全局配置完全相同的 {@link ObjectMapper} 实例。
     *
     * <p>适用于需要临时定制配置（如开启/关闭特定特性）且不影响全局的场景。
     */
    public static ObjectMapper copyObjectMapper() {
        return OBJECT_MAPPER.copy();
    }

    /**
     * 清理类型缓存，释放内存。
     *
     * <p>正常情况下无需调用；如应用中动态生成了大量不同泛型类型，
     * 可定期调用此方法防止缓存膨胀。
     */
    public static void clearTypeCache() {
        TYPE_CACHE.invalidateAll();
        log.debug("[clearTypeCache] Type cache cleared.");
    }

    // ======================== 序列化 ========================

    /**
     * 将对象序列化为紧凑格式的 JSON 字符串。
     *
     * @param obj 待序列化对象（可为 {@code null}，序列化结果为 {@code "null"}）
     * @return JSON 字符串
     * @throws JacksonException 序列化失败时抛出
     */
    public static String toJsonString(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to serialize object to JSON string", e);
        }
    }

    /**
     * 安全版本：将对象序列化为 JSON 字符串，异常时返回 {@code null} 并记录警告日志。
     */
    public static String toJsonStringOrNull(Object obj) {
        try {
            return toJsonString(obj);
        } catch (JacksonException e) {
            log.warn("[toJsonStringOrNull] serialize failed, objType={}",
                    obj != null ? obj.getClass().getName() : "null", e);
            return null;
        }
    }

    /**
     * 将对象序列化为格式化的（缩进）JSON 字符串，便于调试。
     */
    public static String toPrettyJsonString(Object obj) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to serialize object to pretty JSON string", e);
        }
    }

    /**
     * 安全版本：格式化序列化，异常时返回 {@code null} 并记录警告日志。
     */
    public static String toPrettyJsonStringOrNull(Object obj) {
        try {
            return toPrettyJsonString(obj);
        } catch (JacksonException e) {
            log.warn("[toPrettyJsonStringOrNull] serialize failed, objType={}",
                    obj != null ? obj.getClass().getName() : "null", e);
            return null;
        }
    }

    /**
     * 将对象序列化为字节数组。
     */
    public static byte[] toJsonBytes(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new JacksonException("Failed to serialize object to JSON bytes", e);
        }
    }

    /**
     * 将对象序列化并写入输出流（不自动关闭流）。
     */
    public static void writeValue(OutputStream out, Object obj) {
        Objects.requireNonNull(out, "out must not be null");
        try {
            OBJECT_MAPPER.writeValue(out, obj);
        } catch (IOException e) {
            throw new JacksonException("Failed to write JSON to OutputStream", e);
        }
    }

    // ======================== 反序列化 ========================

    /**
     * 将 JSON 字符串解析为指定类型对象。
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        requireNonEmptyJson(json);
        Objects.requireNonNull(clazz, "clazz must not be null");
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw parseException("parseObject", json, clazz.getSimpleName(), e);
        }
    }

    /**
     * 安全版本：解析 JSON，异常时返回 {@code null} 并记录警告日志。
     */
    public static <T> T parseObjectOrNull(String json, Class<T> clazz) {
        try {
            return parseObject(json, clazz);
        } catch (JacksonException e) {
            log.warn("[parseObjectOrNull] parse failed, targetType={}, json={}",
                    clazz != null ? clazz.getSimpleName() : "null", abbreviate(json), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为复杂类型对象（支持泛型、集合等）。
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        requireNonEmptyJson(json);
        Objects.requireNonNull(typeReference, "typeReference must not be null");
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw parseException("parseObject", json, typeReference.getType().getTypeName(), e);
        }
    }

    /**
     * 安全版本：按 {@link TypeReference} 解析，异常时返回 {@code null}。
     */
    public static <T> T parseObjectOrNull(String json, TypeReference<T> typeReference) {
        try {
            return parseObject(json, typeReference);
        } catch (JacksonException e) {
            log.warn("[parseObjectOrNull] parse failed, targetType={}, json={}",
                    typeReference != null ? typeReference.getType().getTypeName() : "null",
                    abbreviate(json), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为指定 {@link Type} 的对象。
     */
    public static <T> T parseObject(String json, Type type) {
        requireNonEmptyJson(json);
        Objects.requireNonNull(type, "type must not be null");
        try {
            JavaType javaType = getCachedJavaType(type);
            return OBJECT_MAPPER.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw parseException("parseObject", json, type.getTypeName(), e);
        }
    }

    /**
     * 安全版本：按 {@link Type} 解析，异常时返回 {@code null}。
     */
    public static <T> T parseObjectOrNull(String json, Type type) {
        try {
            return parseObject(json, type);
        } catch (JacksonException e) {
            log.warn("[parseObjectOrNull] parse failed, targetType={}, json={}",
                    type != null ? type.getTypeName() : "null", abbreviate(json), e);
            return null;
        }
    }

    /**
     * 从输入流解析 JSON 为指定类型对象（不自动关闭流）。
     */
    public static <T> T parseObject(InputStream inputStream, Class<T> clazz) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");
        try {
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (IOException e) {
            throw new JacksonException(
                    "Failed to parse InputStream to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * 从输入流解析 JSON 为复杂类型对象（不自动关闭流）。
     */
    public static <T> T parseObject(InputStream inputStream, TypeReference<T> typeReference) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(typeReference, "typeReference must not be null");
        try {
            return OBJECT_MAPPER.readValue(inputStream, typeReference);
        } catch (IOException e) {
            throw new JacksonException("Failed to parse InputStream with TypeReference", e);
        }
    }

    /**
     * 从字节数组解析 JSON 为指定类型对象。
     */
    public static <T> T parseObject(byte[] bytes, Class<T> clazz) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new JacksonException(
                    "Failed to parse byte[] to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * 从字节数组解析 JSON 为复杂类型对象。
     */
    public static <T> T parseObject(byte[] bytes, TypeReference<T> typeReference) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(typeReference, "typeReference must not be null");
        try {
            return OBJECT_MAPPER.readValue(bytes, typeReference);
        } catch (IOException e) {
            throw new JacksonException("Failed to parse byte[] with TypeReference", e);
        }
    }

    /**
     * 将 JSON 数组字符串解析为 {@link List} 集合。
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        requireNonEmptyJson(json);
        Objects.requireNonNull(clazz, "clazz must not be null");
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw parseException("parseArray", json, "List<" + clazz.getSimpleName() + ">", e);
        }
    }

    /**
     * 安全版本：解析 JSON 数组为 {@link List}，异常时返回 {@code null}。
     */
    public static <T> List<T> parseArrayOrNull(String json, Class<T> clazz) {
        try {
            return parseArray(json, clazz);
        } catch (JacksonException e) {
            log.warn("[parseArrayOrNull] parse failed, elementType={}, json={}",
                    clazz != null ? clazz.getSimpleName() : "null", abbreviate(json), e);
            return null;
        }
    }

    // ======================== JSON 树模型 ========================

    /**
     * 将 JSON 字符串解析为 {@link JsonNode} 树模型，用于动态操作。
     */
    public static JsonNode readTree(String json) {
        requireNonEmptyJson(json);
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw parseException("readTree", json, "JsonNode", e);
        }
    }

    /**
     * 创建一个空的 {@link ObjectNode}，用于构建 JSON 对象。
     */
    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    // ======================== 对象转换 ========================

    /**
     * 将已知对象转换为指定类型的对象（基于类型转换）。
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        Objects.requireNonNull(toValueType, "toValueType must not be null");
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new JacksonException(
                    "Failed to convert value to " + toValueType.getName(), e);
        }
    }

    /**
     * 将已知对象转换为复杂类型对象（支持泛型）。
     */
    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueType) {
        Objects.requireNonNull(toValueType, "toValueType must not be null");
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new JacksonException("Failed to convert value with TypeReference", e);
        }
    }

    // ======================== 内部工具方法 ========================

    /**
     * 从缓存中获取或构建 {@link JavaType}，减少重复构造开销。
     */
    private static JavaType getCachedJavaType(Type type) {
        JavaType cached = TYPE_CACHE.getIfPresent(type);
        if (cached != null) {
            return cached;
        }
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
        TYPE_CACHE.put(type, javaType);
        return javaType;
    }

    /**
     * 校验 JSON 字符串非空。
     */
    private static void requireNonEmptyJson(String json) {
        if (json == null || json.isEmpty()) {
            throw new JacksonException("JSON string is null or empty");
        }
    }

    /**
     * 构造解析异常，JSON 只截取前 {@value #JSON_ABBREVIATE_LENGTH} 字符。
     */
    private static JacksonException parseException(String operation, String json,
                                                   String targetType, Throwable cause) {
        return new JacksonException(
                "Failed to " + operation + " to " + targetType
                        + ", json=" + abbreviate(json), cause);
    }

    /**
     * 截断文本到指定长度，用于日志与异常消息，避免泄露完整敏感数据。
     */
    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= JSON_ABBREVIATE_LENGTH) {
            return text;
        }
        return text.substring(0, JSON_ABBREVIATE_LENGTH)
                + "...(total " + text.length() + " chars)";
    }

    // ======================== 自定义异常 ========================

    /**
     * Jackson 运行时异常，将受检异常包装为非受检异常。
     */
    public static class JacksonException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        public JacksonException(String message) {
            super(message);
        }

        public JacksonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
