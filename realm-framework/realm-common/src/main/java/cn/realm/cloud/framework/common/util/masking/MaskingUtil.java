package cn.realm.cloud.framework.common.util.masking;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具类（Masking Utility）。
 *
 * <p>提供两类能力：
 * <ol>
 *   <li><b>单值脱敏</b>：手机号、身份证、银行卡、邮箱、姓名、地址、密码、IP、车牌、护照、统一社会信用代码等；</li>
 *   <li><b>文本脱敏</b>：从一段文本（日志 / JSON / 报错信息）中自动识别并脱敏敏感信息。</li>
 * </ol>
 *
 * <h2>✅ 适用场景（可以放心使用）</h2>
 * <ul>
 *   <li><b>接口返回前对 DTO 字段脱敏</b>：例如用户列表接口返回手机号、邮箱、身份证时调用
 *       {@link #mobilePhone(String)}、{@link #email(String)}、{@link #idCard(String)}。</li>
 *   <li><b>前端展示前的格式化</b>：如"我的订单"页面展示银行卡后 4 位，
 *       调用 {@link #bankCard(String)}。</li>
 *   <li><b>对少量、结构清晰的字段做脱敏</b>：入参明确是手机号就是手机号，正则不会跑偏。</li>
 *   <li><b>测试/预发环境的日志打印</b>：用于排查问题时快速遮蔽敏感信息。</li>
 *   <li><b>导出报表、对账单、消息推送</b>等对单值脱敏的展示型场景。</li>
 * </ul>
 *
 * <h2>⚠️ 需谨慎使用的场景（需评估后再用）</h2>
 * <ul>
 *   <li><b>生产环境日志脱敏</b>：正则存在误杀风险，
 *       16~19 位订单号、流水号、雪花 ID 会被误判为银行卡；18 位纯数字订单号会被误判为身份证。
 *       建议先按字段名白名单（phone/idCard/email/password/token）做定向脱敏，
 *       或在 Logback 层集成自定义 {@code Converter}，而非全量正则扫描。</li>
 *   <li><b>高 QPS 接口的实时脱敏</b>：{@link #maskText(String)} 会顺序执行 7 个正则 + 7 次全串扫描。
 *       若已知敏感类型，请使用 {@link #maskText(String, Set)} 按需脱敏。</li>
 *   <li><b>依赖开发者主动调用的场景</b>：工具类不会自动生效，第三方库日志、
 *       框架异常堆栈、SQL 日志等无法覆盖，建议配合 Logback 集成或 AOP 使用。</li>
 * </ul>
 *
 * <h2>❌ 不适用场景（不应用它兜底）</h2>
 * <ul>
 *   <li><b>数据合规</b>：GDPR / 个保法要求的是可证明的、全链路的、可审计的数据保护，
 *       单点脱敏工具无法作为合规证据，需结合数据库层加密 + 访问审计 + 密钥管理。</li>
 *   <li><b>密钥、Token、密码的存储安全</b>：本类只做展示层遮蔽，
 *       真实存储必须使用加密（如 AES）或哈希（如 BCrypt）。</li>
 *   <li><b>Base64 / URL 编码后的敏感信息</b>：本类不处理编码形态，
 *       需先解码再脱敏或使用专门的编码感知方案。</li>
 * </ul>
 *
 * <p><b>线程安全</b>：本类所有方法均为无状态纯函数，线程安全。
 * <p><b>不可继承</b>：本类为 {@code final}，禁止被继承与实例化。
 * <p><b>JDK 兼容</b>：仅依赖 JDK 8+，无第三方依赖。
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class MaskingUtil {

    // ==================== 常量 ====================

    /**
     * 默认掩码字符
     */
    private static final char DEFAULT_MASK_CHAR = '*';

    /**
     * 固定长度的掩码（用于密码等）
     */
    private static final String FIXED_MASK = "******";

    /**
     * 邮箱本地部分替换用的固定掩码
     */
    private static final String EMAIL_LOCAL_MASK = "***";

    // ==================== 正则表达式 ====================

    /**
     * 手机号：11 位，1 开头，第二位 3-9；前后不能是数字
     */
    private static final Pattern P_MOBILE = Pattern.compile(
            "(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    /**
     * 身份证：18 位，含校验位 X；前后不能是数字。
     * <p>年份仅支持 1800-2099。
     */
    private static final Pattern P_ID_CARD = Pattern.compile(
            "(?<!\\d)([1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx])(?!\\d)");

    /**
     * 银行卡：16~19 位纯数字；前后不能是数字
     */
    private static final Pattern P_BANK_CARD = Pattern.compile(
            "(?<!\\d)(\\d{16,19})(?!\\d)");

    /**
     * 邮箱
     */
    private static final Pattern P_EMAIL = Pattern.compile(
            "([\\w.%+-]+)@([\\w-]+(?:\\.[\\w-]+)+)");

    /**
     * IPv4（禁止前导零；每段 0-255）
     */
    private static final Pattern P_IPV4 = Pattern.compile(
            "(?<!\\d)((?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
                    + "(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3})(?!\\d)");

    /**
     * 中国护照：
     * <ul>
     *   <li>因私普通护照：E/G + 8 位数字</li>
     *   <li>因公普通护照：P + 7 位数字</li>
     *   <li>外交护照：D/S + 7 位数字</li>
     * </ul>
     */
    private static final Pattern P_PASSPORT = Pattern.compile(
            "(?<![A-Za-z0-9])((?:[EeGg]\\d{8}|[PpDdSs]\\d{7}))(?![A-Za-z0-9])");

    /**
     * 统一社会信用代码：18 位，字符集为数字与排除 I O S V Z 的大写字母。
     *
     * <p>GB 32100-2015 规定字符集为：{@code 0123456789ABCDEFGHJKLMNPQRTUWXY}。
     * 为避免与 18 位纯数字身份证冲突，调用方需保证串中至少含一个大写字母，
     * 校验逻辑已下沉到 {@link #maskText(String, Set)} 中处理。
     */
    private static final Pattern P_UNIFIED_CREDIT_CODE = Pattern.compile(
            "(?<![A-Z0-9])([0-9A-HJ-NP-RT-UWXY]{18})(?![A-Z0-9])");

    /**
     * 纯数字判断（用于银行卡合法性校验）
     */
    private static final Pattern P_PURE_DIGITS = Pattern.compile("\\d+");

    /**
     * 空白字符（预编译，避免每次调用重新编译）
     */
    private static final Pattern P_WHITESPACE = Pattern.compile("\\s");

    /**
     * 私有构造，禁止实例化
     */
    private MaskingUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    // ==================== 通用脱敏方法 ====================

    /**
     * 通用脱敏：保留左侧 {@code left} 位与右侧 {@code right} 位，中间以 {@code *} 替换。
     *
     * <p><b>典型场景</b>：任意自定义字段的脱敏，如会员号、账号、工号、车牌号等。
     *
     * <pre>
     *   mask("1234567890", 3, 2) -> "123*****90"
     *   mask("abc",        1, 1) -> "a*c"
     *   mask("abc",        2, 2) -> "***"       （left+right>=len 时全部替换）
     * </pre>
     *
     * @param str   原始字符串，允许为 {@code null}
     * @param left  保留左侧位数（&lt;0 视为 0）
     * @param right 保留右侧位数（&lt;0 视为 0）
     * @return 脱敏后的字符串；入参为 {@code null}/空串时原样返回
     */
    public static String mask(String str, int left, int right) {
        return mask(str, left, right, DEFAULT_MASK_CHAR);
    }

    /**
     * 通用脱敏（可自定义掩码字符）。
     *
     * <pre>
     *   mask("13812345678", 3, 4, '•') -> "138••••5678"
     * </pre>
     *
     * @param maskChar 掩码字符，传 {@code '\0'} 时使用默认 {@code *}
     */
    public static String mask(String str, int left, int right, char maskChar) {
        if (isBlank(str)) {
            return str;
        }
        int len = str.length();
        int l = Math.max(left, 0);
        int r = Math.max(right, 0);
        if (l + r >= len) {
            return repeat(len, maskChar);
        }
        StringBuilder sb = new StringBuilder(len);
        sb.append(str, 0, l);
        sb.append(repeat(len - l - r, maskChar));
        sb.append(str, len - r, len);
        return sb.toString();
    }

    /**
     * 全部字符替换为 {@code *}。
     *
     * <p><b>典型场景</b>：完全隐藏字段内容，如密钥、Token、会话 ID 的展示层遮蔽。
     */
    public static String maskAll(String str) {
        return isBlank(str) ? str : repeat(str.length(), DEFAULT_MASK_CHAR);
    }

    // ==================== 具体业务类型脱敏 ====================

    /**
     * 手机号脱敏：保留前 3 位与后 4 位。
     * <pre>13812345678 -> 138****5678</pre>
     */
    public static String mobilePhone(String mobile) {
        return isBlank(mobile) ? mobile : mask(mobile.trim(), 3, 4);
    }

    /**
     * 身份证脱敏：保留前 6 位与后 4 位。
     * <pre>110101199003071234 -> 110101********1234</pre>
     */
    public static String idCard(String idCard) {
        if (isBlank(idCard)) {
            return idCard;
        }
        String s = idCard.trim();
        return s.length() < 8 ? repeat(s.length(), DEFAULT_MASK_CHAR) : mask(s, 6, 4);
    }

    /**
     * 银行卡脱敏：保留前 4 位与后 4 位，中间用空格分隔，掩码位数与隐藏位数一致。
     * <pre>
     *   6217001234567890    -> 6217 ******** 7890
     *   6217001234567890123 -> 6217 *********** 0123
     * </pre>
     *
     * <p><b>健壮性</b>：
     * <ul>
     *   <li>若输入含非数字字符，则降级为通用脱敏 {@code mask(s, 4, 4)}；</li>
     *   <li>若输入位数不在 16~19 之间，也降级为通用脱敏；</li>
     *   <li>若输入位数 &le; 8，则全部替换为 {@code *}。</li>
     * </ul>
     */
    public static String bankCard(String cardNo) {
        if (isBlank(cardNo)) {
            return cardNo;
        }
        String s = P_WHITESPACE.matcher(cardNo).replaceAll("");
        if (!P_PURE_DIGITS.matcher(s).matches()) {
            return mask(s, 4, 4);
        }
        if (s.length() <= 8) {
            return repeat(s.length(), DEFAULT_MASK_CHAR);
        }
        if (s.length() < 16 || s.length() > 19) {
            return mask(s, 4, 4);
        }
        int maskLen = s.length() - 8;
        return s.substring(0, 4)
                + " " + repeat(maskLen, DEFAULT_MASK_CHAR) + " "
                + s.substring(s.length() - 4);
    }

    /**
     * 邮箱脱敏：本地部分保留首字符，其余以 {@code ***} 替换。
     * <pre>zhangsan@example.com -> z***@example.com</pre>
     *
     * <p><b>健壮性</b>：非法邮箱（无 {@code @}、多 {@code @}、{@code @} 位于首尾）降级处理：
     * <ul>
     *   <li>{@code @} 前有内容时，保留 {@code @} 及其后部分，仅脱敏本地部分；</li>
     *   <li>{@code @} 位于首位或不存在时，整体降级为通用脱敏。</li>
     * </ul>
     */
    public static String email(String email) {
        if (isBlank(email)) {
            return email;
        }
        String s = email.trim();
        int at = s.indexOf('@');
        int lastAt = s.lastIndexOf('@');
        // 合法邮箱：本地保留首字符 + *** + 完整域名
        if (at > 0 && at == lastAt && at < s.length() - 1) {
            return s.charAt(0) + EMAIL_LOCAL_MASK + s.substring(at);
        }
        // 非法但 @ 前有内容：保留 @ 后的结构信息，仅脱敏本地部分
        if (at > 0) {
            return mask(s.substring(0, at), 1, 0) + s.substring(at);
        }
        // 无 @ 或 @ 位于首位：整体降级
        return mask(s, 1, 0);
    }

    /**
     * 中文姓名脱敏：保留姓氏首字与名字末字，其余替换为 {@code *}。
     * <pre>
     *   张       -> 张
     *   张三     -> 张*
     *   张小三   -> 张*三
     *   欧阳娜娜 -> 欧**娜
     * </pre>
     *
     * <p><b>注意</b>：该策略为「保首保尾」，不区分单姓与复姓。
     * 对于复姓两字姓名（如 {@code 欧阳}）会返回 {@code 欧*}，
     * 若业务对复姓展示有要求，请自行扩展复姓字典。
     */
    public static String chineseName(String name) {
        if (isBlank(name)) {
            return name;
        }
        String s = name.trim();
        int len = s.length();
        if (len == 1) {
            return s;
        }
        if (len == 2) {
            return s.charAt(0) + String.valueOf(DEFAULT_MASK_CHAR);
        }
        return s.charAt(0) + repeat(len - 2, DEFAULT_MASK_CHAR) + s.charAt(len - 1);
    }

    /**
     * 地址脱敏：保留前 6 位（一般覆盖省市区），其余替换。
     * <pre>北京市海淀区中关村大街1号 -> 北京市海淀区*******</pre>
     */
    public static String address(String address) {
        if (isBlank(address)) {
            return address;
        }
        String s = address.trim();
        return mask(s, Math.min(6, s.length()), 0);
    }

    /**
     * 密码脱敏：统一返回固定长度掩码。
     * <pre>任意非空密码 -> ******</pre>
     *
     * <p><b>注意</b>：本方法仅用于展示层遮蔽，真实存储必须使用加密或哈希（如 BCrypt）。
     */
    public static String password(String password) {
        return isBlank(password) ? password : FIXED_MASK;
    }

    /**
     * IP 脱敏：IPv4 保留前 3 段，最后一段替换为 {@code *}。
     * <pre>192.168.1.100 -> 192.168.1.*</pre>
     */
    public static String ip(String ip) {
        if (isBlank(ip)) {
            return ip;
        }
        String s = ip.trim();
        int idx = s.lastIndexOf('.');
        return idx > 0 ? s.substring(0, idx + 1) + DEFAULT_MASK_CHAR
                : repeat(s.length(), DEFAULT_MASK_CHAR);
    }

    /**
     * 车牌号脱敏：保留前 2 位与后 2 位。
     * <pre>京A12345 -> 京A***45</pre>
     */
    public static String carLicense(String license) {
        return isBlank(license) ? license : mask(license.trim(), 2, 2);
    }

    /**
     * 护照号脱敏：保留首字母与后 2 位。
     * <pre>E12345678 -> E******78</pre>
     */
    public static String passport(String passport) {
        return isBlank(passport) ? passport : mask(passport.trim(), 1, 2);
    }

    /**
     * 统一社会信用代码脱敏：保留前 2 位与后 2 位。
     * <pre>91310000MA1FL1234X -> 91**************4X</pre>
     */
    public static String unifiedCreditCode(String code) {
        return isBlank(code) ? code : mask(code.trim(), 2, 2);
    }

    // ==================== 文本批量脱敏 ====================

    /**
     * 文本脱敏类型。用于按需脱敏，避免不必要的全串扫描。
     */
    public enum MaskType {
        /**
         * 邮箱
         */
        EMAIL,
        /**
         * 身份证
         */
        ID_CARD,
        /**
         * 统一社会信用代码
         */
        UNIFIED_CREDIT_CODE,
        /**
         * 银行卡
         */
        BANK_CARD,
        /**
         * 护照
         */
        PASSPORT,
        /**
         * 手机号
         */
        MOBILE,
        /**
         * IPv4
         */
        IPV4
    }

    /**
     * 对一段文本（日志 / JSON / 异常信息）中的敏感信息批量脱敏，
     * 等价于 {@code maskText(text, EnumSet.allOf(MaskType.class))}。
     *
     * <p><b>生产环境慎用</b>：正则存在误杀风险，
     * 16~19 位订单号、雪花 ID 会被误判为银行卡；18 位纯数字订单号会被误判为身份证。
     * 生产环境建议使用 {@link #maskText(String, Set)} 按需脱敏，
     * 或在 Logback 层集成自定义 {@code Converter}。
     *
     * @param text 原始文本，允许为 {@code null}
     * @return 脱敏后的文本；入参为 {@code null}/空串时原样返回
     */
    public static String maskText(String text) {
        return maskText(text, EnumSet.allOf(MaskType.class));
    }

    /**
     * 按需对文本中的敏感信息批量脱敏。
     *
     * <p><b>处理顺序</b>（不可颠倒）：
     * <ol>
     *   <li>邮箱（避免邮箱本地部分的数字被银行卡/手机号正则提前破坏）</li>
     *   <li>身份证（18 位数字 + X，先于银行卡/统一社会信用代码）</li>
     *   <li>统一社会信用代码（18 位含字母；纯数字会被后续规则处理）</li>
     *   <li>银行卡（16~19 位数字）</li>
     *   <li>护照</li>
     *   <li>手机号（11 位）</li>
     *   <li>IPv4</li>
     * </ol>
     *
     * @param text  原始文本，允许为 {@code null}
     * @param types 需要脱敏的类型集合；为 {@code null}/空时原样返回
     * @return 脱敏后的文本；入参为 {@code null}/空串时原样返回
     */
    public static String maskText(String text, Set<MaskType> types) {
        if (isBlank(text) || types == null || types.isEmpty()) {
            return text;
        }
        String result = text;
        // 1. 邮箱优先：本地部分可能是手机号、银行卡号、身份证号等，提前遮蔽避免互相干扰
        if (types.contains(MaskType.EMAIL)) {
            result = replaceWithFunction(result, P_EMAIL,
                    m -> email(m.group(1) + "@" + m.group(2)));
        }
        // 2. 身份证（18 位数字 + X）
        if (types.contains(MaskType.ID_CARD)) {
            result = replaceWithFunction(result, P_ID_CARD,
                    m -> idCard(m.group(1)));
        }
        // 3. 统一社会信用代码（18 位字符集，且至少包含一个大写字母）
        if (types.contains(MaskType.UNIFIED_CREDIT_CODE)) {
            result = replaceWithFunction(result, P_UNIFIED_CREDIT_CODE, m -> {
                String code = m.group(1);
                // 纯数字的 18 位串交给后续银行卡/身份证规则处理
                if (!containsUpperCaseLetter(code)) {
                    return m.group(0);
                }
                return unifiedCreditCode(code);
            });
        }
        // 4. 银行卡（16~19 位数字）
        if (types.contains(MaskType.BANK_CARD)) {
            result = replaceWithFunction(result, P_BANK_CARD,
                    m -> bankCard(m.group(1)));
        }
        // 5. 护照
        if (types.contains(MaskType.PASSPORT)) {
            result = replaceWithFunction(result, P_PASSPORT,
                    m -> passport(m.group(1)));
        }
        // 6. 手机号（11 位）
        if (types.contains(MaskType.MOBILE)) {
            result = replaceWithFunction(result, P_MOBILE,
                    m -> mobilePhone(m.group(1)));
        }
        // 7. IPv4
        if (types.contains(MaskType.IPV4)) {
            result = replaceWithFunction(result, P_IPV4,
                    m -> ip(m.group(1)));
        }
        return result;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 生成指定长度的掩码串。
     */
    private static String repeat(int count, char maskChar) {
        if (count <= 0) {
            return "";
        }
        char c = maskChar == '\0' ? DEFAULT_MASK_CHAR : maskChar;
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    /**
     * 判断字符串是否为 {@code null} 或空白。
     * <p>直接逐字符判断，避免 {@code trim()} 生成临时字符串。
     */
    private static boolean isBlank(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        for (int i = 0, len = str.length(); i < len; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串中是否含有大写英文字母（{@code A-Z}）。
     */
    private static boolean containsUpperCaseLetter(String s) {
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return true;
            }
        }
        return false;
    }

    /**
     * 兼容 JDK 8 的正则替换（{@link Matcher#replaceAll(Function)} 是 JDK 9+ API）。
     */
    private static String replaceWithFunction(String text, Pattern pattern,
                                              Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
