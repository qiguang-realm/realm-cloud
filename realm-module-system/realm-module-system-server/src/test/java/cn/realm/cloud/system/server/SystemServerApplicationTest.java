package cn.realm.cloud.system.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemServer 应用测试类 / Test class for SystemServer application.
 */
public class SystemServerApplicationTest {

    private static final Logger logger = LoggerFactory.getLogger(SystemServerApplicationTest.class);

    @Test
    @DisplayName("日志输出测试 / Logging test")
    void testLogging() {
        logger.info("这是一条测试日志 / This is a test log message");
        logger.debug("调试级别日志 / Debug level log");
        logger.warn("警告级别日志 / Warn level log");
        assertTrue(true, "日志输出不应抛出异常 / Logging should not throw exception");
    }

    @Test
    @DisplayName("简单加法测试 / Simple addition test")
    void testAddition() {
        int a = 1;
        int b = 2;
        int expected = 3;
        int actual = a + b;
        logger.info("计算 {} + {} = {}", a, b, actual);
        assertEquals(expected, actual, "1 + 2 应该等于 3 / 1 + 2 should equal 3");
    }

    @Test
    @DisplayName("字符串拼接测试 / String concatenation test")
    void testStringConcat() {
        String hello = "Hello";
        String world = "World";
        String result = hello + ", " + world + "!";
        logger.info("拼接结果 / Concatenated result: {}", result);
        assertEquals("Hello, World!", result);
        assertTrue(result.startsWith("Hello"));
        assertTrue(result.endsWith("!"));
    }

    @Test
    @DisplayName("空值断言测试 / Null assertion test")
    void testNull() {
        String value = null;
        assertNull(value, "value 应为 null / value should be null");
        String nonNull = "not null";
        assertNotNull(nonNull, "nonNull 不应为 null / nonNull should not be null");
    }

    @Test
    @DisplayName("当前时间测试 / Current time test")
    void testCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        logger.info("当前时间 / Current time: {}", now);
        assertNotNull(now);
        assertTrue(now.getYear() >= 2024, "年份应大于等于 2024 / Year should be >= 2024");
    }

    @Test
    @DisplayName("异常断言测试 / Exception assertion test")
    void testException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> {
                    throw new IllegalArgumentException("测试异常 / Test exception");
                }
        );
        logger.info("捕获到预期异常 / Caught expected exception: {}", exception.getMessage());
        assertEquals("测试异常 / Test exception", exception.getMessage());
    }
}
