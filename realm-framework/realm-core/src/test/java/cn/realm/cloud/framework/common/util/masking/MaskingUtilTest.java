package cn.realm.cloud.framework.common.util.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MaskingUtil} 单元测试。
 *
 * <p>覆盖：单值脱敏的所有业务方法、文本批量脱敏的处理顺序与按需脱敏、边界与降级策略、线程安全。
 *
 * <p><b>关于误杀风险</b>：{@code maskText(String, Set)} 省略了前序规则时，后续规则需自行兜底，
 * 例如仅启用 {@code BANK_CARD} 时，18 位纯数字身份证会被 {@code BANK_CARD} 正则
 * {@code \d{16,19}} 命中。这是按需脱敏的已知取舍，相关测试已显式声明。
 */
@DisplayName("MaskingUtil 敏感信息脱敏工具")
class MaskingUtilTest {

    // ========================================================================
    // mask
    // ========================================================================

    @Nested
    @DisplayName("mask - 通用脱敏")
    class MaskMethodTest {

        @Test
        @DisplayName("null 入参原样返回")
        void null_input_returns_null() {
            assertNull(MaskingUtil.mask(null, 3, 4));
            assertNull(MaskingUtil.mask(null, 3, 4, '#'));
        }

        @Test
        @DisplayName("空串原样返回")
        void empty_input_returns_empty() {
            assertEquals("", MaskingUtil.mask("", 3, 4));
        }

        @Test
        @DisplayName("纯空白串原样返回")
        void blank_input_returns_blank() {
            assertEquals("   ", MaskingUtil.mask("   ", 3, 4));
            assertEquals("\t\n", MaskingUtil.mask("\t\n", 3, 4));
        }

        @ParameterizedTest(name = "[{index}] mask(\"{0}\", {1}, {2}) = \"{3}\"")
        @CsvSource({
                // 常规场景
                "'1234567890', 3, 2, '123*****90'",
                "'abc',       1, 1, 'a*c'",
                "'abcd',      1, 1, 'a**d'",
                "'12345678',  0, 4, '****5678'",
                "'12345678',  4, 0, '1234****'",
                // 边界：left + right >= len 时全部替换
                "'abc',       2, 2, '***'",
                "'abc',       3, 0, '***'",
                "'abc',       0, 3, '***'",
                "'abc',       5, 5, '***'",
                // 负数视为 0
                "'abc',      -1, -1, '***'",
                "'abc',       1, -1, 'a**'",
                "'abc',      -1,  1, '**c'",
        })
        void mask_parameterized(String input, int left, int right, String expected) {
            assertEquals(expected, MaskingUtil.mask(input, left, right));
        }

        @Test
        @DisplayName("可自定义掩码字符")
        void custom_mask_char() {
            assertEquals("138••••5678", MaskingUtil.mask("13812345678", 3, 4, '•'));
            assertEquals("138####5678", MaskingUtil.mask("13812345678", 3, 4, '#'));
        }

        @Test
        @DisplayName("掩码字符为 '\\0' 时使用默认 '*'")
        void null_char_falls_back_to_default() {
            assertEquals("138****5678", MaskingUtil.mask("13812345678", 3, 4, '\0'));
        }
    }

    // ========================================================================
    // maskAll
    // ========================================================================

    @Nested
    @DisplayName("maskAll - 全部替换")
    class MaskAllTest {

        @ParameterizedTest(name = "[{index}] maskAll(\"{0}\") = \"{1}\"")
        @CsvSource({
                "'abc',    '***'",
                "'abcdef', '******'",
                "'张三',   '**'",
                "'12345',  '*****'",
        })
        void maskAll_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.maskAll(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.maskAll(null));
            assertEquals("", MaskingUtil.maskAll(""));
            assertEquals("   ", MaskingUtil.maskAll("   "));
        }
    }

    // ========================================================================
    // mobilePhone
    // ========================================================================

    @Nested
    @DisplayName("mobilePhone - 手机号脱敏")
    class MobilePhoneTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "13812345678, '138****5678'",
                "15912345678, '159****5678'",
                "18800000000, '188****0000'",
                "12345678901, '123****8901'",
                "1381234567,  '138***4567'",
                "123,         '***'",
        })
        void mobile_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.mobilePhone(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.mobilePhone(null));
            assertEquals("", MaskingUtil.mobilePhone(""));
            assertEquals("   ", MaskingUtil.mobilePhone("   "));
        }

        @Test
        @DisplayName("首尾空白被 trim")
        void trim_whitespace() {
            assertEquals("138****5678", MaskingUtil.mobilePhone("  13812345678  "));
        }
    }

    // ========================================================================
    // idCard
    // ========================================================================

    @Nested
    @DisplayName("idCard - 身份证脱敏")
    class IdCardTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "110101199003071234, '110101********1234'",
                "11010119900307123X, '110101********123X'",
                "11010119900307123x, '110101********123x'",
                // 长度为 8 时全部替换
                "12345678,           '********'",
                // 长度小于 8 时全部替换
                "1234567,            '*******'",
                "123456,             '******'",
        })
        void idCard_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.idCard(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.idCard(null));
            assertEquals("", MaskingUtil.idCard(""));
            assertEquals("   ", MaskingUtil.idCard("   "));
        }
    }

    // ========================================================================
    // bankCard
    // ========================================================================

    @Nested
    @DisplayName("bankCard - 银行卡脱敏")
    class BankCardTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                // 16 位标准卡号
                "6217001234567890,       '6217 ******** 7890'",
                // 19 位标准卡号
                "6217001234567890123,    '6217 *********** 0123'",
                // 含空格的输入会先被去掉空格
                "'6217 0012 3456 7890',  '6217 ******** 7890'",
                // 含制表符/换行
                "'6217\t0012\n3456 7890', '6217 ******** 7890'",
                // 长度 <= 8 全部替换
                "12345678,               '********'",
                "1234,                   '****'",
        })
        void bankCard_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.bankCard(input));
        }

        @Test
        @DisplayName("含非数字字符时降级为通用脱敏")
        void non_digit_fallback() {
            // 去掉空白后为 "1234-5678-9012-3456"（19 位），降级为 mask(s, 4, 4)
            assertEquals("1234***********3456",
                    MaskingUtil.bankCard("1234-5678-9012-3456"));
        }

        @Test
        @DisplayName("位数不在 16~19 之间时降级为通用脱敏")
        void invalid_length_fallback() {
            // 10 位纯数字，mask(s, 4, 4) -> "1234**7890"
            assertEquals("1234**7890", MaskingUtil.bankCard("1234567890"));
            // 20 位纯数字，mask(s, 4, 4)
            assertEquals("1234************7890", MaskingUtil.bankCard("12345678901234567890"));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.bankCard(null));
            assertEquals("", MaskingUtil.bankCard(""));
            assertEquals("   ", MaskingUtil.bankCard("   "));
        }
    }

    // ========================================================================
    // email
    // ========================================================================

    @Nested
    @DisplayName("email - 邮箱脱敏")
    class EmailTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                // 标准邮箱
                "zhangsan@example.com,     'z***@example.com'",
                "a@b.com,                  'a***@b.com'",
                "zhangsan@sub.example.com, 'z***@sub.example.com'",
                // 非法但 @ 前有内容：保留 @ 及之后的结构
                "'zhangsan@',              'z*******@'",
                "'a@b@c.com',              '*@b@c.com'",
                // 无 @：整体降级
                "zhangsan,                 'z*******'",
                "a,                        '*'",
        })
        void email_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.email(input));
        }

        @Test
        @DisplayName("@ 位于首位：整体降级为通用脱敏")
        void at_at_start() {
            // mask("@example.com", 1, 0) -> "@" + 11 个 '*'
            assertEquals("@***********", MaskingUtil.email("@example.com"));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.email(null));
            assertEquals("", MaskingUtil.email(""));
            assertEquals("   ", MaskingUtil.email("   "));
        }
    }

    // ========================================================================
    // chineseName
    // ========================================================================

    @Nested
    @DisplayName("chineseName - 中文姓名脱敏")
    class ChineseNameTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "张,       '张'",
                "张三,     '张*'",
                "李四,     '李*'",
                "张小三,   '张*三'",
                "李小明,   '李*明'",
                "张三丰,   '张*丰'",
                "欧阳娜娜, '欧**娜'",
                // 复姓两字姓名会丢失复姓第二字（文档已说明）
                "欧阳,     '欧*'",
        })
        void chineseName_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.chineseName(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.chineseName(null));
            assertEquals("", MaskingUtil.chineseName(""));
            assertEquals("   ", MaskingUtil.chineseName("   "));
        }
    }

    // ========================================================================
    // address
    // ========================================================================

    @Nested
    @DisplayName("address - 地址脱敏")
    class AddressTest {

        @Test
        @DisplayName("长度大于 6 时保留前 6 位")
        void normal_case() {
            assertEquals("北京市海淀区*******",
                    MaskingUtil.address("北京市海淀区中关村大街1号"));
            assertEquals("上海市浦东新****",
                    MaskingUtil.address("上海市浦东新区xxx"));
            assertEquals("北京市海淀区*",
                    MaskingUtil.address("北京市海淀区中"));
        }

        @Test
        @DisplayName("长度 <= 6 时全部替换")
        void short_address() {
            assertEquals("**", MaskingUtil.address("北京"));
            assertEquals("***", MaskingUtil.address("北京市"));
            assertEquals("******", MaskingUtil.address("北京市海淀区"));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.address(null));
            assertEquals("", MaskingUtil.address(""));
            assertEquals("   ", MaskingUtil.address("   "));
        }
    }

    // ========================================================================
    // password
    // ========================================================================

    @Nested
    @DisplayName("password - 密码脱敏")
    class PasswordTest {

        @ParameterizedTest
        @ValueSource(strings = {"a", "abc", "123456", "P@ssw0rd!", "中文密码"})
        @DisplayName("任意非空密码统一返回固定掩码")
        void non_blank_returns_fixed_mask(String pwd) {
            assertEquals("******", MaskingUtil.password(pwd));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.password(null));
            assertEquals("", MaskingUtil.password(""));
            assertEquals("   ", MaskingUtil.password("   "));
        }
    }

    // ========================================================================
    // ip
    // ========================================================================

    @Nested
    @DisplayName("ip - IP 脱敏")
    class IpTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "192.168.1.100,    '192.168.1.*'",
                "10.0.0.1,         '10.0.0.*'",
                "255.255.255.255,  '255.255.255.*'",
                "192.168.1,        '192.168.*'",
                // 无 '.'：整体替换
                "localhost,        '*********'",
                "1921681100,       '**********'",
        })
        void ip_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.ip(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.ip(null));
            assertEquals("", MaskingUtil.ip(""));
            assertEquals("   ", MaskingUtil.ip("   "));
        }
    }

    // ========================================================================
    // carLicense
    // ========================================================================

    @Nested
    @DisplayName("carLicense - 车牌脱敏")
    class CarLicenseTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "京A12345, '京A***45'",
                "沪B88888, '沪B***88'",
                "粤C12345, '粤C***45'",
                // 长度不足：全部替换
                "AB,       '**'",
                "A,        '*'",
        })
        void carLicense_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.carLicense(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.carLicense(null));
            assertEquals("", MaskingUtil.carLicense(""));
            assertEquals("   ", MaskingUtil.carLicense("   "));
        }
    }

    // ========================================================================
    // passport
    // ========================================================================

    @Nested
    @DisplayName("passport - 护照脱敏")
    class PassportTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "E12345678, 'E******78'",
                "G87654321, 'G******21'",
                "P1234567,  'P*****67'",
                "D1234567,  'D*****67'",
                "S1234567,  'S*****67'",
        })
        void passport_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.passport(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.passport(null));
            assertEquals("", MaskingUtil.passport(""));
            assertEquals("   ", MaskingUtil.passport("   "));
        }
    }

    // ========================================================================
    // unifiedCreditCode
    // ========================================================================

    @Nested
    @DisplayName("unifiedCreditCode - 统一社会信用代码脱敏")
    class UnifiedCreditCodeTest {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
                "91310000MA1FL1234X, '91**************4X'",
                "91110108MA01ABCDEF, '91**************EF'",
        })
        void unifiedCreditCode_parameterized(String input, String expected) {
            assertEquals(expected, MaskingUtil.unifiedCreditCode(input));
        }

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.unifiedCreditCode(null));
            assertEquals("", MaskingUtil.unifiedCreditCode(""));
            assertEquals("   ", MaskingUtil.unifiedCreditCode("   "));
        }
    }

    // ========================================================================
    // maskText
    // ========================================================================

    @Nested
    @DisplayName("maskText - 文本批量脱敏")
    class MaskTextTest {

        @Test
        @DisplayName("null / 空串 / 空白串原样返回")
        void blank_inputs() {
            assertNull(MaskingUtil.maskText(null));
            assertEquals("", MaskingUtil.maskText(""));
            assertEquals("   ", MaskingUtil.maskText("   "));
        }

        @Test
        @DisplayName("同时命中多种敏感信息")
        void multiple_types() {
            String text = "手机:13812345678，邮箱:zhangsan@example.com，"
                    + "身份证:110101199003071234，卡号:6217001234567890，"
                    + "IP:192.168.1.100，护照:E12345678，"
                    + "企业:91310000MA1FL1234X";
            String expected = "手机:138****5678，邮箱:z***@example.com，"
                    + "身份证:110101********1234，卡号:6217 ******** 7890，"
                    + "IP:192.168.1.*，护照:E******78，"
                    + "企业:91**************4X";
            assertEquals(expected, MaskingUtil.maskText(text));
        }

        @Test
        @DisplayName("邮箱优先：本地部分为手机号时不会二次脱敏")
        void email_priority_over_mobile() {
            // 步骤 1 邮箱先处理，手机号被替换后不会再被 MOBILE 正则命中
            assertEquals("联系1***@example.com",
                    MaskingUtil.maskText("联系13812345678@example.com"));
        }

        @Test
        @DisplayName("身份证优先于银行卡：18 位纯数字走身份证脱敏")
        void idCard_before_bankCard() {
            // 18 位纯数字既满足身份证也满足银行卡，处理顺序决定结果
            assertEquals("110101********1234",
                    MaskingUtil.maskText("110101199003071234"));
        }

        @Test
        @DisplayName("统一社会信用代码仅匹配含大写字母的 18 位串")
        void unified_credit_code_requires_letter() {
            // 含字母 -> 走统一社会信用代码
            assertEquals("91**************4X",
                    MaskingUtil.maskText("91310000MA1FL1234X"));
        }

        @Test
        @DisplayName("16~19 位纯数字会被误判为银行卡（文档已声明风险）")
        void bank_card_false_positive() {
            // 16 位订单号会被识别为银行卡号
            assertEquals("订单号1234 ******** 3456",
                    MaskingUtil.maskText("订单号1234567890123456"));
        }

        @Test
        @DisplayName("IPv4 单独命中")
        void ipv4_only() {
            assertEquals("IP:192.168.1.*", MaskingUtil.maskText("IP:192.168.1.100"));
        }

        @Test
        @DisplayName("含非规范形态 IPv4（前导零）不应被识别")
        void ipv4_leading_zero() {
            // 前导零非规范形态，预期不命中（依赖 IPv4 正则的加严）
            assertEquals("IP:192.168.001.100",
                    MaskingUtil.maskText("IP:192.168.001.100"));
        }

        @Test
        @DisplayName("IPv4 边界值 0 与 255 命中，256 越界不命中")
        void ipv4_boundaries() {
            assertEquals("0.0.0.*", MaskingUtil.maskText("0.0.0.0"));
            assertEquals("255.255.255.*", MaskingUtil.maskText("255.255.255.255"));
            assertEquals("256.1.1.1", MaskingUtil.maskText("256.1.1.1"));
        }
    }

    // ========================================================================
    // maskText(String, Set<MaskType>)
    // ========================================================================

    @Nested
    @DisplayName("maskText - 按需脱敏")
    class MaskTextWithTypesTest {

        private static final String TEXT =
                "手机:13812345678，邮箱:zhangsan@example.com，"
                        + "身份证:110101199003071234，卡号:6217001234567890";

        @Test
        @DisplayName("types 为 null 时原样返回")
        void null_types() {
            assertEquals(TEXT, MaskingUtil.maskText(TEXT, null));
        }

        @Test
        @DisplayName("types 为空集时原样返回")
        void empty_types() {
            assertEquals(TEXT, MaskingUtil.maskText(TEXT, Collections.emptySet()));
        }

        @Test
        @DisplayName("仅脱敏手机号")
        void mobile_only() {
            Set<MaskingUtil.MaskType> types = EnumSet.of(MaskingUtil.MaskType.MOBILE);
            String expected = "手机:138****5678，邮箱:zhangsan@example.com，"
                    + "身份证:110101199003071234，卡号:6217001234567890";
            assertEquals(expected, MaskingUtil.maskText(TEXT, types));
        }

        @Test
        @DisplayName("仅脱敏邮箱")
        void email_only() {
            Set<MaskingUtil.MaskType> types = EnumSet.of(MaskingUtil.MaskType.EMAIL);
            String expected = "手机:13812345678，邮箱:z***@example.com，"
                    + "身份证:110101199003071234，卡号:6217001234567890";
            assertEquals(expected, MaskingUtil.maskText(TEXT, types));
        }

        @Test
        @DisplayName("脱敏手机号 + 身份证")
        void mobile_and_id_card() {
            Set<MaskingUtil.MaskType> types = EnumSet.of(
                    MaskingUtil.MaskType.MOBILE, MaskingUtil.MaskType.ID_CARD);
            String expected = "手机:138****5678，邮箱:zhangsan@example.com，"
                    + "身份证:110101********1234，卡号:6217001234567890";
            assertEquals(expected, MaskingUtil.maskText(TEXT, types));
        }

        /**
         * <b>已知风险</b>：仅启用 {@code BANK_CARD} 时，18 位纯数字身份证号同时满足
         * {@code BANK_CARD} 正则 {@code \d{16,19}}，会被当作银行卡脱敏。
         *
         * <p>若希望避免误伤，请同时启用 {@link MaskingUtil.MaskType#ID_CARD}
         * （参见 {@link #id_card_and_bank_card_both_enabled()}），
         * 或在工具类中为按需脱敏加一层兜底（见类文档的"生产环境慎用"章节）。
         */
        @Test
        @DisplayName("仅启用 BANK_CARD 时，18 位纯数字身份证会被误判为银行卡（已知风险）")
        void bank_card_only() {
            Set<MaskingUtil.MaskType> types = EnumSet.of(MaskingUtil.MaskType.BANK_CARD);
            // 18 位纯数字同时满足银行卡正则，被替换为 "1101 ********** 1234"
            String expected = "手机:13812345678，邮箱:zhangsan@example.com，"
                    + "身份证:1101 ********** 1234，卡号:6217 ******** 7890";
            assertEquals(expected, MaskingUtil.maskText(TEXT, types));
        }

        @Test
        @DisplayName("同时启用 ID_CARD + BANK_CARD 时，身份证优先于银行卡")
        void id_card_and_bank_card_both_enabled() {
            Set<MaskingUtil.MaskType> types = EnumSet.of(
                    MaskingUtil.MaskType.ID_CARD, MaskingUtil.MaskType.BANK_CARD);
            // 身份证先于银行卡处理，18 位纯数字走 ID_CARD 逻辑，不会被误判
            String expected = "手机:13812345678，邮箱:zhangsan@example.com，"
                    + "身份证:110101********1234，卡号:6217 ******** 7890";
            assertEquals(expected, MaskingUtil.maskText(TEXT, types));
        }

        @Test
        @DisplayName("全量等价：maskText(text) == maskText(text, allOf)")
        void all_types_equivalence() {
            assertEquals(MaskingUtil.maskText(TEXT),
                    MaskingUtil.maskText(TEXT, EnumSet.allOf(MaskingUtil.MaskType.class)));
        }
    }

    // ========================================================================
    // 线程安全
    // ========================================================================

    @Nested
    @DisplayName("线程安全")
    class ThreadSafetyTest {

        @Test
        @DisplayName("并发调用结果一致")
        void concurrent_calls_are_consistent() throws InterruptedException {
            final int threads = 16;
            final int iterations = 2_000;
            final String input = "13812345678";
            final String expected = "138****5678";

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicBoolean failed = new AtomicBoolean(false);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < iterations; j++) {
                            if (!expected.equals(MaskingUtil.mobilePhone(input))) {
                                failed.set(true);
                                return;
                            }
                            if (!"z***@example.com".equals(
                                    MaskingUtil.email("zhangsan@example.com"))) {
                                failed.set(true);
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failed.set(true);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务超时未完成");
            pool.shutdownNow();
            assertFalse(failed.get(), "并发场景下脱敏结果不一致");
        }
    }
}
