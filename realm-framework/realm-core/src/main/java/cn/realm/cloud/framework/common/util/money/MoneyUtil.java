package cn.realm.cloud.framework.common.util.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 金额工具类。
 *
 * <h2>线程安全</h2>
 * 所有方法均为静态方法，无可变共享状态；{@link DecimalFormat} 与 {@link NumberFormat}
 * 均在方法内部创建，每次调用独立实例。JDK 明确声明 DecimalFormat/NumberFormat
 * “generally not synchronized”，推荐为每次格式化创建独立实例。
 *
 * <h2>舍入策略</h2>
 * 默认 {@link RoundingMode#HALF_UP}，与 {@link #DEFAULT_SCALE} = 2 位小数配套，
 * 适用于人民币等两位小数货币。所有格式化方法显式设置同一舍入模式，并保证
 * <em>计算值与展示值同源同规则</em>。
 *
 * <h2>货币小数位</h2>
 * {@link #DEFAULT_SCALE} 仅为<strong>业务默认值</strong>，不代表所有货币均为 2 位。
 * ISO 4217 规定各货币的 minor unit 不同：JPY、KRW、VND 等为 0 位；BHD、KWD、
 * JOD、OMR、TND 等为 3 位；CLF、UYW 可达 4 位。涉及非 2 位货币时，
 * 必须使用带 {@code scale} 参数的重载或 {@link Currency#getDefaultFractionDigits()}。
 *
 * <p>本类提供与 {@link Currency} 配套的便捷重载：
 * {@link #of(String, Currency)}、{@link #ofStrict(String, Currency)}、
 * {@link #round(BigDecimal, Currency)}、{@link #zero(Currency)}、
 * {@link #formatWithSymbol(BigDecimal, Currency)}，
 * 自动取 {@link Currency#getDefaultFractionDigits()} 作为 scale；
 * 伪货币（如 XXX、XTS，{@code getDefaultFractionDigits() == -1}）抛 IAE，
 * 调用方需显式指定 scale。</p>
 *
 * <h2>浮点禁令</h2>
 * 金额计算<strong>禁止使用 {@code float} / {@code double}</strong>。IEEE 754
 * 二进制浮点无法精确表示多数十进制小数，货币场景必须使用
 * {@link BigDecimal} 或最小单位整数（分）。
 *
 * <h2>null 契约</h2>
 * <p><strong>⚠ 本类所有显式空值校验统一抛 {@link IllegalArgumentException}，不抛
 * {@link NullPointerException}。</strong>调用方捕获逻辑请按 IAE 处理；若希望统一
 * 为 NPE 语义，请在服务入口用 {@link java.util.Objects#requireNonNull(Object)} 兜底。</p>
 * <table border="1">
 *   <caption>null 参数行为</caption>
 *   <tr><th>方法组</th><th>null 行为</th></tr>
 *   <tr><td>{@code of(String)}、{@code ofStrict}、{@code nullToZero}</td><td>返回零值</td></tr>
 *   <tr><td>{@code round}、{@code format}</td><td>返回零值或零值字符串</td></tr>
 *   <tr><td>{@code isValid / isValidLiteral}</td><td>返回 {@code false}</td></tr>
 *   <tr><td>{@code isNullOrZero}</td><td>返回 {@code true}</td></tr>
 *   <tr><td>{@code add/sum/subtract/multiply/divide}</td><td>抛 IAE</td></tr>
 *   <tr><td>{@code max/min}（含集合版）、{@code clamp}、{@code compare}</td><td>抛 IAE</td></tr>
 *   <tr><td>{@code eq/gt/ge/lt/le}</td><td>抛 IAE</td></tr>
 *   <tr><td>{@code isPositive/isNegative/isZero/abs/negate}</td><td>抛 IAE</td></tr>
 *   <tr><td>{@code yuanToFen}（含重载）、{@code toPlainString}</td><td>抛 IAE</td></tr>
 * </table>
 * <p>带 {@link Currency} 参数的重载在 {@code currency} 为 null 或伪货币时抛 IAE；
 * 其余参数行为与对应基础方法一致。</p>
 *
 * <p><strong>重载歧义提示</strong>：{@code formatWithSymbol(amount, null)}
 * 因同时存在 {@code (BigDecimal, Locale)} 与 {@code (BigDecimal, Currency)} 重载，
 * 会在编译期产生歧义；请改用无参 {@link #formatWithSymbol(BigDecimal)} 或显式类型转换。</p>
 *
 * <h2>关于“负零”</h2>
 * <p>{@link BigDecimal} <strong>不存在负零</strong>：{@code new BigDecimal("-0.00")}
 * 的 {@code signum() == 0}，其 {@link BigDecimal#toString()} 为 {@code "0.00"}。
 * 因此本类无需（也不应声称）对“负零”做归一化。</p>
 *
 * <h2>重载形态（重要）</h2>
 * <p>{@code add} <strong>仅保留两种形态</strong>：</p>
 * <ul>
 *   <li>{@code add(BigDecimal...)} —— 默认 scale=2 的 varargs 求和</li>
 *   <li>{@code add(BigDecimal[] values, int scale[, RoundingMode])} —— 显式 scale 的数组求和</li>
 * </ul>
 * <p><strong>不再提供</strong> {@code add(int scale, BigDecimal...)} 或
 * {@code add(int, RoundingMode, BigDecimal...)}。历史上：</p>
 * <ul>
 *   <li>{@code add(2)} 返回 2 位小数的零值<em>而非金额 2</em>（静默陷阱）；</li>
 *   <li>{@code add(2, null)} 在编译期产生重载歧义。</li>
 * </ul>
 * <p>现在这两种写法<strong>编译期即报错</strong>。需要零值请用 {@link #zero(int)}；
 * 需要数组 + scale 求和请用 {@link #add(BigDecimal[], int)}。</p>
 *
 * <h2>序列化建议</h2>
 * 金额跨进程传输请使用 {@link #toPlainString(BigDecimal)} 或最小单位整数（分），
 * <strong>禁止使用 JSON 浮点数</strong>。IETF 与 W3C 均要求 monetary values 为
 * 精确十进制字符串；{@link BigDecimal#toString()} 可能输出科学计数法，
 * 序列化时必须使用 {@link BigDecimal#toPlainString()}。
 *
 * @author QI Guang
 */
public final class MoneyUtil {

    private MoneyUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 默认小数位数（业务默认，非全货币通则；非 2 位货币须显式指定 scale）
     */
    public static final int DEFAULT_SCALE = 2;

    /**
     * 默认舍入模式：四舍五入（HALF_UP）
     */
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    /**
     * 零值（scale = 2，正零）。
     * <p>若需其他精度请用 {@link #zero(int)} 或 {@link #zero(int, RoundingMode)}。</p>
     */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);

    /**
     * 一分钱（{@code 0.01}），供调用方表示最小货币单位。
     * <p><strong>注意</strong>：仅适用于 2 位小数货币（CNY、USD、EUR 等）；
     * JPY、KRW 等 0 位小数货币无“分”概念，BHD、KWD 等 3 位小数货币
     * 应使用 {@code new BigDecimal("0.001")}。</p>
     */
    public static final BigDecimal CENT = new BigDecimal("0.01");

    private static final Locale FORMAT_LOCALE = Locale.CHINA;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String DEFAULT_PATTERN = "#,##0.00";
    private static final Pattern STRICT_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    // ==================== 创建 / 转换 ====================

    /**
     * 字符串转金额（默认 2 位小数，{@link RoundingMode#HALF_UP}）。
     *
     * <p>null、空串或纯空白返回 {@link #ZERO}；超过 2 位小数按 HALF_UP 舍入。</p>
     *
     * <p>接受符合 {@link BigDecimal#BigDecimal(String)} 语法的十进制字符串，
     * <strong>包括科学计数法</strong>（如 {@code "1.23E+3"}）；
     * <strong>前后空白（含全角空格、NBSP 等 Unicode 空白）会被去除</strong>；
     * <em>不接受分组符与货币符号</em>
     * （如 {@code "1,234.56"}、{@code "¥1234.56"} 会抛 NFE）。
     * {@code "NaN"}、{@code "Infinity"} 同样会抛 NFE。</p>
     *
     * <p>若输入来自用户表单/CSV 等不可信来源，建议改用
     * {@link #ofStrict(String)} 拒绝科学计数法与非法字符。</p>
     *
     * @throws NumberFormatException if the string is not a valid decimal number
     */
    public static BigDecimal of(String value) {
        return of(value, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * 字符串转金额，按 {@code currency} 的标准小数位（{@link RoundingMode#HALF_UP}）。
     *
     * <p>等价于 {@code of(value, currency.getDefaultFractionDigits(), HALF_UP)}；
     * 例如 JPY 返回 scale=0 的金额，BHD 返回 scale=3 的金额。</p>
     *
     * <p>空白判定与 {@link #of(String, int, RoundingMode)} 一致（含 Unicode 空白）。</p>
     *
     * @param value    金额字符串；null/空白返回对应货币 scale 的零值
     * @param currency 货币；用于取 {@link Currency#getDefaultFractionDigits()}
     * @throws NumberFormatException    if the string is not a valid decimal number
     * @throws IllegalArgumentException if currency is null or a pseudo-currency
     *                                  (e.g. XXX, XTS)
     */
    public static BigDecimal of(String value, Currency currency) {
        return of(value, requireFractionDigits(currency), DEFAULT_ROUNDING);
    }

    /**
     * 字符串转金额，指定精度和舍入模式。
     *
     * @param value 金额字符串；null/空白（含 Unicode 空白）返回对应 scale 的零值
     * @param scale 小数位数，必须 &gt;= 0
     * @param mode  舍入模式；null 使用 {@link #DEFAULT_ROUNDING}
     * @throws NumberFormatException    if the string is not a valid decimal number
     * @throws IllegalArgumentException if scale is negative
     */
    public static BigDecimal of(String value, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        RoundingMode m = mode == null ? DEFAULT_ROUNDING : mode;
        String s = trimUnicode(value);
        if (s == null || s.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale, m);
        }
        return new BigDecimal(s).setScale(scale, m);
    }

    /**
     * 严格模式字符串转金额（默认 2 位小数，HALF_UP）。
     *
     * <p>与 {@link #of(String)} 的区别：<strong>拒绝科学计数法</strong>
     * （{@code E}/{@code e}）与任何非数字字符（含分组符、货币符号、正号 {@code +}）。
     * 仅接受 {@code [-]}<em>digits</em>{@code [.}<em>digits</em>{@code ]} 形式。
     * 适用于用户表单、CSV/Excel 导入等<strong>不可信来源</strong>。</p>
     *
     * <p>null、空串或纯空白仍返回对应 scale 的零值，与 {@link #of(String)} 一致；
     * “空白”的判定与 {@link #of(String, int, RoundingMode)} 相同，含 Unicode 空白。</p>
     *
     * @throws NumberFormatException    if the string is not a strict decimal number
     * @throws IllegalArgumentException if scale is negative
     */
    public static BigDecimal ofStrict(String value) {
        return ofStrict(value, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * 严格模式字符串转金额，按 {@code currency} 的标准小数位（HALF_UP）。
     *
     * <p>语义同 {@link #ofStrict(String, int, RoundingMode)}，仅 scale 由货币决定。</p>
     *
     * @param value    金额字符串；null/空白返回对应货币 scale 的零值
     * @param currency 货币；用于取 {@link Currency#getDefaultFractionDigits()}
     * @throws NumberFormatException    if the string is not a strict decimal number
     * @throws IllegalArgumentException if currency is null or a pseudo-currency
     */
    public static BigDecimal ofStrict(String value, Currency currency) {
        return ofStrict(value, requireFractionDigits(currency), DEFAULT_ROUNDING);
    }

    /**
     * 严格模式字符串转金额，指定精度和舍入模式。
     *
     * @param value 金额字符串；null/空白（含 Unicode 空白）返回对应 scale 的零值
     * @param scale 小数位数，必须 &gt;= 0
     * @param mode  舍入模式；null 使用 {@link #DEFAULT_ROUNDING}
     * @throws NumberFormatException    if the string is not a strict decimal number
     * @throws IllegalArgumentException if scale is negative
     */
    public static BigDecimal ofStrict(String value, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        RoundingMode m = mode == null ? DEFAULT_ROUNDING : mode;
        String s = trimUnicode(value);
        if (s == null || s.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale, m);
        }
        if (!STRICT_DECIMAL.matcher(s).matches()) {
            throw new NumberFormatException(
                    "金额字符串格式非法（仅允许可选负号 + 数字 + 可选小数点 + 数字，禁止科学计数法/分组符/货币符号）: " + value);
        }
        return new BigDecimal(s).setScale(scale, m);
    }

    /**
     * long 金额（元）转金额对象，scale = 2。
     *
     * <p>仅接受 {@code long}；{@code int} 会自动提升；{@code double}/{@code float}
     * 因违反浮点禁令不会编译通过。若已有 {@link BigDecimal} 输入，请使用
     * {@link #round(BigDecimal)} 或 {@link #of(String)}。</p>
     */
    public static BigDecimal ofYuan(long yuan) {
        return BigDecimal.valueOf(yuan).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * long 金额（分）转金额对象。等价于 {@link #fenToYuan(long)}。
     */
    public static BigDecimal ofCent(long fen) {
        return fenToYuan(fen);
    }

    /**
     * 空值安全转换：null 返回 {@link #ZERO}（scale = 2），非 null 原样返回，不改变 scale。
     *
     * <p><strong>scale 不一致提示</strong>：{@code nullToZero(new BigDecimal("1"))}
     * 返回 scale=0 的 {@code 1}，而 {@code nullToZero(null)} 返回 scale=2 的 {@code 0.00}。
     * 若下游依赖 scale（如 {@link #toPlainString}、格式化、JSON 序列化），
     * 请改用 {@link #round(BigDecimal)} 或 {@link #of(String)} 统一精度，
     * 否则可能出现 {@code "1"} 与 {@code "1.00"} 并存。</p>
     */
    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    // ==================== 零值 ====================

    /**
     * 返回 {@link #ZERO}（scale = 2）。等价于旧版 {@code add()}。
     */
    public static BigDecimal zero() {
        return ZERO;
    }

    /**
     * 返回指定 scale 的零值（正零，HALF_UP）。等价于旧版 {@code add(scale)}，
     * 但方法名明确表达“零值”语义，<strong>不会</strong>被误读为“加金额 scale”。
     *
     * @throws IllegalArgumentException if scale is negative
     */
    public static BigDecimal zero(int scale) {
        return zero(scale, DEFAULT_ROUNDING);
    }

    /**
     * 返回指定 scale 的零值（正零）。
     *
     * @param scale 小数位数，必须 &gt;= 0
     * @param mode  舍入模式；null 使用 {@link #DEFAULT_ROUNDING}（对零值无实际影响）
     * @throws IllegalArgumentException if scale is negative
     */
    public static BigDecimal zero(int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        return BigDecimal.ZERO.setScale(scale, mode == null ? DEFAULT_ROUNDING : mode);
    }

    /**
     * 返回按 {@code currency} 标准小数位的零值（正零）。
     *
     * <p>等价于 {@code zero(currency.getDefaultFractionDigits())}；
     * 例如 JPY 返回 scale=0 的 {@code 0}，BHD 返回 scale=3 的 {@code 0.000}。</p>
     *
     * @param currency 货币；用于取 {@link Currency#getDefaultFractionDigits()}
     * @throws IllegalArgumentException if currency is null or a pseudo-currency
     */
    public static BigDecimal zero(Currency currency) {
        return zero(requireFractionDigits(currency));
    }

    // ==================== 运算：求和 ====================

    /**
     * varargs 求和（默认 2 位小数，HALF_UP 舍入）。
     *
     * <p>空数组返回 {@link #ZERO}。这是 {@code add} 的 varargs 形态；
     * 原 {@code add(int scale, ...)} / {@code add(int, RoundingMode, ...)}
     * 重载因 {@code add(2)} 陷阱与 {@code add(2, null)} 编译期歧义已移除。
     * 需要显式 scale 请用 {@link #add(BigDecimal[], int)}；需要零值请用 {@link #zero(int)}。</p>
     *
     * @param values 加数；不允许为 null 或含 null 元素；空数组返回 {@link #ZERO}
     * @throws IllegalArgumentException if values is null or contains null element
     */
    @SafeVarargs
    public static BigDecimal add(BigDecimal... values) {
        return add(values, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * 数组求和，指定精度（默认 HALF_UP 舍入）。
     *
     * <p>参数顺序为 {@code (values, scale)}，与
     * {@link #subtract(BigDecimal, BigDecimal, int, RoundingMode)} 等“scale/mode 在后”
     * 的形态保持一致。</p>
     *
     * @param values 加数；不允许为 null 或含 null 元素；空数组返回对应 scale 的零值
     * @throws IllegalArgumentException if scale is negative, values is null,
     *                                  or contains null element
     */
    public static BigDecimal add(BigDecimal[] values, int scale) {
        return add(values, scale, DEFAULT_ROUNDING);
    }

    /**
     * 数组求和，指定精度和舍入模式。
     *
     * @param values 加数；不允许为 null 或含 null 元素；空数组返回对应 scale 的零值
     * @throws IllegalArgumentException if scale is negative, values is null,
     *                                  or contains null element
     */
    public static BigDecimal add(BigDecimal[] values, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        if (values == null) {
            throw new IllegalArgumentException("金额参数数组不能为 null");
        }
        RoundingMode m = mode == null ? DEFAULT_ROUNDING : mode;
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v == null) {
                throw new IllegalArgumentException("金额参数不能为 null");
            }
            result = result.add(v);
        }
        return result.setScale(scale, m);
    }

    /**
     * 集合求和（默认 2 位小数，HALF_UP 舍入）。
     */
    public static BigDecimal sum(Collection<BigDecimal> values) {
        return sum(values, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * 集合求和，指定精度（默认 HALF_UP 舍入）。
     */
    public static BigDecimal sum(Collection<BigDecimal> values, int scale) {
        return sum(values, scale, DEFAULT_ROUNDING);
    }

    /**
     * 集合求和，指定精度和舍入模式。
     *
     * @throws IllegalArgumentException if scale is negative, values is null,
     *                                  or contains null element
     */
    public static BigDecimal sum(Collection<BigDecimal> values, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        if (values == null) {
            throw new IllegalArgumentException("金额集合不能为 null");
        }
        RoundingMode m = mode == null ? DEFAULT_ROUNDING : mode;
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v == null) {
                throw new IllegalArgumentException("金额集合不能包含 null 元素");
            }
            result = result.add(v);
        }
        return result.setScale(scale, m);
    }

    // ==================== 运算：减 / 乘 / 除 ====================

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return subtract(a, b, DEFAULT_SCALE);
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b, int scale) {
        return subtract(a, b, scale, DEFAULT_ROUNDING);
    }

    /**
     * 减法，指定精度和舍入模式。
     *
     * @throws IllegalArgumentException if scale is negative, a is null, or b is null
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        requireNonNull(a, "被减数");
        requireNonNull(b, "减数");
        return a.subtract(b).setScale(scale, mode == null ? DEFAULT_ROUNDING : mode);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return multiply(a, b, DEFAULT_SCALE);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b, int scale) {
        return multiply(a, b, scale, DEFAULT_ROUNDING);
    }

    /**
     * 乘法，指定精度和舍入模式。
     *
     * @throws IllegalArgumentException if scale is negative, a is null, or b is null
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        requireNonNull(a, "乘数 a");
        requireNonNull(b, "乘数 b");
        return a.multiply(b).setScale(scale, mode == null ? DEFAULT_ROUNDING : mode);
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return divide(a, b, DEFAULT_SCALE);
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale) {
        return divide(a, b, scale, DEFAULT_ROUNDING);
    }

    /**
     * 除法，指定精度和舍入模式。
     *
     * <p>BigDecimal 除法若不指定 scale 和舍入模式，在除不尽时会抛
     * {@link ArithmeticException}；本方法强制要求二者，避免不确定行为。</p>
     *
     * @throws IllegalArgumentException if scale is negative, a is null, or b is null
     * @throws ArithmeticException      if b is zero
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        requireNonNull(a, "被除数");
        requireNonNull(b, "除数");
        if (b.signum() == 0) {
            throw new ArithmeticException("除数不能为 0");
        }
        return a.divide(b, scale, mode == null ? DEFAULT_ROUNDING : mode);
    }

    // ==================== 舍入 ====================

    public static BigDecimal round(BigDecimal value) {
        return round(value, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    public static BigDecimal round(BigDecimal value, int scale) {
        return round(value, scale, DEFAULT_ROUNDING);
    }

    /**
     * 舍入到指定精度和舍入模式。null 返回对应 scale 的零值。
     *
     * @throws IllegalArgumentException if scale is negative
     */
    public static BigDecimal round(BigDecimal value, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        RoundingMode m = mode == null ? DEFAULT_ROUNDING : mode;
        if (value == null) {
            return BigDecimal.ZERO.setScale(scale, m);
        }
        return value.setScale(scale, m);
    }

    /**
     * 舍入到 {@code currency} 的标准小数位（HALF_UP）。null 返回对应货币 scale 的零值。
     *
     * <p>等价于 {@code round(value, currency.getDefaultFractionDigits(), HALF_UP)}；
     * 例如 {@code round(new BigDecimal("1.2345"), JPY)} 返回 scale=0 的 {@code 1}，
     * 而 {@code round(..., BHD)} 返回 scale=3 的 {@code 1.235}。</p>
     *
     * @param value    金额；null 返回对应货币 scale 的零值
     * @param currency 货币；用于取 {@link Currency#getDefaultFractionDigits()}
     * @throws IllegalArgumentException if currency is null or a pseudo-currency
     */
    public static BigDecimal round(BigDecimal value, Currency currency) {
        return round(value, requireFractionDigits(currency), DEFAULT_ROUNDING);
    }

    // ==================== 比较 ====================

    /**
     * 比较两个金额（基于 {@link BigDecimal#compareTo}，忽略 scale）。
     *
     * <p>返回负数 / 零 / 正数分别表示 {@code a < b}、{@code a == b}、{@code a > b}。
     * 供排序（{@code Comparator.comparing}）等场景使用；若只需布尔判断，
     * 请用 {@link #eq}/{@link #gt}/{@link #ge}/{@link #lt}/{@link #le}。</p>
     *
     * @throws IllegalArgumentException if a or b is null
     */
    public static int compare(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b);
    }

    /**
     * 金额相等判断（基于 {@link BigDecimal#compareTo}，忽略 scale）。
     *
     * <p><strong>不要使用 {@link BigDecimal#equals(Object)} 比较金额</strong>：
     * equals 同时比较 value 和 scale，{@code 2.0} 与 {@code 2.00} 返回 false；
     * compareTo 只比较数值，是金额判等的唯一正确方式。</p>
     *
     * @throws IllegalArgumentException if a or b is null
     */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) == 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) > 0;
    }

    public static boolean ge(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) < 0;
    }

    public static boolean le(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) <= 0;
    }

    /**
     * 取较大值。不改变返回值的 scale。
     *
     * @throws IllegalArgumentException if a or b is null
     */
    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * 取较小值。不改变返回值的 scale。
     *
     * @throws IllegalArgumentException if a or b is null
     */
    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        requireNonNull(a, "金额 a");
        requireNonNull(b, "金额 b");
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * 集合取最大值。不改变返回值的 scale（与二元 {@link #max(BigDecimal, BigDecimal)} 一致）。
     *
     * @param values 金额集合；不允许为 null 或空，也不允许含 null 元素
     * @throws IllegalArgumentException if values is null, empty, or contains null element
     */
    public static BigDecimal max(Collection<BigDecimal> values) {
        if (values == null) {
            throw new IllegalArgumentException("金额集合不能为 null");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("金额集合不能为空");
        }
        BigDecimal result = null;
        for (BigDecimal v : values) {
            if (v == null) {
                throw new IllegalArgumentException("金额集合不能包含 null 元素");
            }
            if (result == null || v.compareTo(result) > 0) {
                result = v;
            }
        }
        return result;
    }

    /**
     * 集合取最小值。不改变返回值的 scale（与二元 {@link #min(BigDecimal, BigDecimal)} 一致）。
     *
     * @param values 金额集合；不允许为 null 或空，也不允许含 null 元素
     * @throws IllegalArgumentException if values is null, empty, or contains null element
     */
    public static BigDecimal min(Collection<BigDecimal> values) {
        if (values == null) {
            throw new IllegalArgumentException("金额集合不能为 null");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("金额集合不能为空");
        }
        BigDecimal result = null;
        for (BigDecimal v : values) {
            if (v == null) {
                throw new IllegalArgumentException("金额集合不能包含 null 元素");
            }
            if (result == null || v.compareTo(result) < 0) {
                result = v;
            }
        }
        return result;
    }

    /**
     * 钳制：把 {@code value} 限制在 {@code [min, max]} 闭区间内。
     *
     * <p>返回值为 {@code value}、{@code min} 或 {@code max} 之一，
     * <strong>不改变 scale</strong>。若 {@code min > max} 抛 IAE。</p>
     *
     * @throws IllegalArgumentException if any argument is null, or min > max
     */
    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        requireNonNull(value, "金额");
        requireNonNull(min, "下限");
        requireNonNull(max, "上限");
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("下限不能大于上限");
        }
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    public static boolean isPositive(BigDecimal v) {
        requireNonNull(v, "金额");
        return v.signum() > 0;
    }

    public static boolean isNegative(BigDecimal v) {
        requireNonNull(v, "金额");
        return v.signum() < 0;
    }

    public static boolean isZero(BigDecimal v) {
        requireNonNull(v, "金额");
        return v.signum() == 0;
    }

    /**
     * null 或 signum = 0 返回 {@code true}。
     */
    public static boolean isNullOrZero(BigDecimal v) {
        return v == null || v.signum() == 0;
    }

    // ==================== 符号 ====================

    public static BigDecimal abs(BigDecimal v) {
        requireNonNull(v, "金额");
        return v.abs();
    }

    public static BigDecimal negate(BigDecimal v) {
        requireNonNull(v, "金额");
        return v.negate();
    }

    // ==================== 元 / 分 互转 ====================

    /**
     * 元 → 分。分以下按 {@link RoundingMode#HALF_UP} 舍入。
     *
     * <p>使用 {@link BigDecimal#longValueExact()} 而非 {@link BigDecimal#longValue()}，
     * 后者在溢出时静默截断，会掩盖数据错误。</p>
     *
     * <p><strong>负数舍入语义</strong>：{@code HALF_UP} 对负数是“远离零”舍入
     * （{@code -0.005 → -1 分}）。借方/冲销等对客户敏感的场景，建议使用
     * {@link #yuanToFen(BigDecimal, RoundingMode)} 显式指定
     * {@link RoundingMode#HALF_EVEN} 或 {@link RoundingMode#DOWN}，
     * 避免“多扣 0.5 分”或“少扣 0.4 分”的对账争议。</p>
     *
     * @throws IllegalArgumentException if yuan is null
     * @throws ArithmeticException      if the scaled result overflows {@code long}
     */
    public static long yuanToFen(BigDecimal yuan) {
        return yuanToFen(yuan, DEFAULT_ROUNDING);
    }

    /**
     * 元 → 分，指定舍入模式。
     *
     * <p>借方/冲销等对客户敏感的场景，可显式传入 {@link RoundingMode#HALF_EVEN}
     * 或 {@link RoundingMode#DOWN}，避免默认 {@code HALF_UP} 对负数“远离零”
     * 舍入带来的对账争议。</p>
     *
     * @param yuan 金额（元）；不允许 null
     * @param mode 舍入模式；null 使用 {@link #DEFAULT_ROUNDING}
     * @return 金额（分）
     * @throws IllegalArgumentException if yuan is null
     * @throws ArithmeticException      if the scaled result overflows {@code long}
     */
    public static long yuanToFen(BigDecimal yuan, RoundingMode mode) {
        requireNonNull(yuan, "金额");
        RoundingMode m = mode == null ? DEFAULT_ROUNDING : mode;
        return yuan.multiply(HUNDRED)
                .setScale(0, m)
                .longValueExact();
    }

    /**
     * 分 → 元。使用 {@link BigDecimal#movePointLeft(int)} 做精确移位，
     * 不存在舍入风险（{@code fen / 100} 对 long 恒为精确值）。
     *
     * @param fen 金额（分）
     * @return 金额（元，scale = 2）
     */
    public static BigDecimal fenToYuan(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(DEFAULT_SCALE);
    }

    // ==================== 格式化 ====================

    /**
     * 千分位格式化：{@code 1234567.8 → "1,234,567.80"}。
     * null 格式化为 {@code "0.00"}。
     *
     * <p>注：{@link BigDecimal} 不存在负零，{@code new BigDecimal("-0.00")}
     * 的 {@code signum() == 0}，会正常格式化为 {@code "0.00"}。</p>
     */
    public static String format(BigDecimal amount) {
        return formatInternal(amount, DEFAULT_PATTERN);
    }

    /**
     * 自定义格式化模式。小数位由 {@code pattern} 决定，<em>不再强制 2 位</em>。
     *
     * <p>明确拒绝含 {@code 'E'} / {@code 'e'} / {@code '%'} / {@code '‰'} / {@code '¤'}
     * 的 pattern：金额格式化不应使用科学计数法、百分比或千分号模式——{@code '%'}
     * 与 {@code '‰'} 会让 {@link DecimalFormat} 把数值分别乘以 100 / 1000 输出，
     * 属于静默的量级污染；{@code '¤'} 是货币符号占位符，会让本方法以
     * {@link Locale#CHINA} 硬编码的人民币符号输出，绕过
     * {@link #formatWithSymbol(BigDecimal, Currency)} 的 2 位货币校验。
     * <strong>上述检查仅作用于引号外字符</strong>：{@code "#,##0.00 'EUR'"}、
     * {@code "#,##0.00'%'"} 等引号内字面量不受限制；引号未闭合时抛 IAE。
     * {@code pattern} 会先去前后空白（含 Unicode 空白），因此前导/尾随空白不会成为字面量。</p>
     *
     * <p>关于 {@code 'e'}：{@link DecimalFormat} 本身并不将 {@code 'e'} 解释为
     * 科学计数法（科学计数法用 {@code 'E'}），本类拒绝 {@code 'e'} 属防御性策略，
     * 避免调用方误以为可写指数形式。</p>
     *
     * <p><strong>locale 说明</strong>：分组符与小数点符号固定按 {@link Locale#CHINA}
     * 解析，与系统 locale 无关；货币符号请用
     * {@link #formatWithSymbol(BigDecimal)} / {@link #formatWithSymbol(BigDecimal, Locale)}
     * / {@link #formatWithSymbol(BigDecimal, Currency)}。</p>
     *
     * @param amount  金额；null 格式化为零值
     * @param pattern 格式化 pattern，如 {@code "#,##0.00"}
     * @throws IllegalArgumentException if pattern is null, blank, contains
     *                                  unsupported characters, or has an
     *                                  unterminated quote
     */
    public static String format(BigDecimal amount, String pattern) {
        String trimmed = trimUnicode(pattern);
        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException("格式化 pattern 不能为空");
        }
        // 仅检查引号外的字符；'EUR'、'%'、'¤' 等引号内字面量不再被误拒。
        // '%' 与 '‰'(U+2030) 会让 DecimalFormat 将数值乘以 100 / 1000，
        // 'E' 触发科学计数法；'e' 属防御性拒绝；'¤'(U+00A4) 是货币符号占位符，
        // 会以硬编码的 FORMAT_LOCALE 符号输出并绕过 formatWithSymbol 的货币校验。
        if (containsUnquoted(trimmed, '%', '\u2030', 'E', 'e', '\u00A4')) {
            throw new IllegalArgumentException(
                    "不支持的 pattern（含百分比、千分号、科学计数法或货币符号占位符 ¤）: "
                            + pattern + "；货币符号请用 formatWithSymbol");
        }
        return formatInternal(amount, trimmed);
    }

    /**
     * 带人民币符号格式化（固定 {@link Locale#CHINA}，2 位小数）。
     * null 格式化为零货币值。
     */
    public static String formatWithSymbol(BigDecimal amount) {
        return formatWithSymbol(amount, FORMAT_LOCALE);
    }

    /**
     * 带货币符号格式化，指定 Locale。
     *
     * <p>固定按 2 位小数舍入与展示，且<strong>显式设置</strong>
     * {@code minimumFractionDigits / maximumFractionDigits = 2}，不依赖 JDK
     * 对 locale 货币的默认推断，保证“计算值与展示值同源同规则”。</p>
     *
     * <p>若 locale 无法确定货币（如 {@link Locale#ROOT}）或对应货币小数位不是 2
     * （如 JPY 0 位、KWD 3 位），抛 {@link IllegalArgumentException}。
     * <strong>如需格式化非 2 位货币</strong>，请用
     * {@link #formatWithSymbol(BigDecimal, Currency)}。</p>
     *
     * <p><strong>输出不具跨 JDK 版本的稳定性</strong>：货币符号的选择（如
     * {@code ¥}(U+00A5) 与 {@code ￥}(U+FFE5)）、符号与数值间是否有空格，
     * 均由 JDK 的 CLDR 数据决定，不同 JDK 版本可能不同。输出仅供<strong>人机展示</strong>，
     * 禁止用于契约性比较或传输/反解析。</p>
     *
     * @throws IllegalArgumentException if locale is null, its currency cannot be
     *                                  determined, or its currency does not use
     *                                  2 decimal digits
     * @see #formatWithSymbol(BigDecimal, Currency)
     */
    public static String formatWithSymbol(BigDecimal amount, Locale locale) {
        if (locale == null) {
            throw new IllegalArgumentException("locale 不能为 null");
        }
        requireTwoDecimalCurrency(locale);
        BigDecimal normalized = normalizeForFormat(amount);
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        nf.setRoundingMode(DEFAULT_ROUNDING);
        nf.setMinimumFractionDigits(DEFAULT_SCALE);
        nf.setMaximumFractionDigits(DEFAULT_SCALE);
        return nf.format(normalized);
    }

    /**
     * 按 {@code currency} 的标准小数位格式化，带货币符号。
     *
     * <p>与 {@link #formatWithSymbol(BigDecimal, Locale)} 的区别：小数位由
     * {@link Currency#getDefaultFractionDigits()} 决定，<strong>不限定 2 位</strong>，
     * 可正确格式化 JPY（0 位）、BHD（3 位）等非 2 位货币。</p>
     *
     * <p>分组符与小数点符号仍按 {@link Locale#CHINA} 解析，货币符号由 {@code currency}
     * 决定；货币符号与数值间是否有空格由 JDK CLDR 数据决定，与 Locale 重载一样
     * <strong>不具跨 JDK 版本稳定性</strong>，输出仅供人机展示，禁止用于契约性比较
     * 或传输/反解析。</p>
     *
     * <p><strong>重载提示</strong>：{@code formatWithSymbol(amount, null)} 因同时存在
     * {@code (BigDecimal, Locale)} 与 {@code (BigDecimal, Currency)} 重载，
     * 在编译期产生歧义；请改用无参 {@link #formatWithSymbol(BigDecimal)} 或显式
     * 类型转换。</p>
     *
     * @param amount   金额；null 格式化为对应货币 scale 的零值
     * @param currency 货币；不允许 null 或伪货币
     * @throws IllegalArgumentException if currency is null or a pseudo-currency
     * @see #formatWithSymbol(BigDecimal, Locale)
     */
    public static String formatWithSymbol(BigDecimal amount, Currency currency) {
        int digits = requireFractionDigits(currency);
        BigDecimal normalized = amount == null
                ? BigDecimal.ZERO.setScale(digits, DEFAULT_ROUNDING)
                : amount.setScale(digits, DEFAULT_ROUNDING);
        NumberFormat nf = NumberFormat.getCurrencyInstance(FORMAT_LOCALE);
        nf.setCurrency(currency);
        nf.setRoundingMode(DEFAULT_ROUNDING);
        nf.setMinimumFractionDigits(digits);
        nf.setMaximumFractionDigits(digits);
        return nf.format(normalized);
    }

    /**
     * 转为无科学计数法、规范化到默认精度（2 位）的十进制字符串，用于序列化/传输。
     *
     * <p>等价于 {@code round(amount).toPlainString()}。IETF 与 W3C 均要求
     * 金额以精确十进制字符串传输，不得使用 JSON 浮点数或科学计数法。</p>
     *
     * @throws IllegalArgumentException if amount is null
     */
    public static String toPlainString(BigDecimal amount) {
        return toPlainString(amount, DEFAULT_SCALE);
    }

    /**
     * 转为指定精度的无科学计数法字符串（按 HALF_UP 舍入到该精度）。
     *
     * @throws IllegalArgumentException if amount is null or scale is negative
     */
    public static String toPlainString(BigDecimal amount, int scale) {
        return toPlainString(amount, scale, DEFAULT_ROUNDING);
    }

    /**
     * 转为指定精度和舍入模式的无科学计数法字符串。
     *
     * <p>与 {@link #round(BigDecimal, int, RoundingMode)} 形态对齐；
     * 默认两参版本固定 {@link #DEFAULT_ROUNDING}，需要 {@code HALF_EVEN}
     * 等模式时使用本方法。</p>
     *
     * @param amount 金额；不允许 null
     * @param scale  小数位数，必须 &gt;= 0
     * @param mode   舍入模式；null 使用 {@link #DEFAULT_ROUNDING}
     * @throws IllegalArgumentException if amount is null or scale is negative
     */
    public static String toPlainString(BigDecimal amount, int scale, RoundingMode mode) {
        requireNonNegativeScale(scale);
        requireNonNull(amount, "金额");
        return amount.setScale(scale, mode == null ? DEFAULT_ROUNDING : mode).toPlainString();
    }

    /**
     * 按 pattern 的小数位精度格式化。先按 pattern 的 maximumFractionDigits
     * 预舍入，保证与 {@link #round} 同规则。
     *
     * <p>{@code pattern} 已由调用方校验非空、已去前后空白且引号成对，此处不再重复校验。</p>
     */
    private static String formatInternal(BigDecimal amount, String pattern) {
        DecimalFormat df = new DecimalFormat(
                pattern,
                DecimalFormatSymbols.getInstance(FORMAT_LOCALE));
        df.setRoundingMode(DEFAULT_ROUNDING);
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        int maxFrac = df.getMaximumFractionDigits();
        v = v.setScale(maxFrac, DEFAULT_ROUNDING);
        return df.format(v);
    }

    /**
     * 格式化前的规范化：null → ZERO，非 null 按 {@link #DEFAULT_SCALE} 舍入。
     * {@link BigDecimal} 不存在负零，无需额外 signum 处理。
     */
    private static BigDecimal normalizeForFormat(BigDecimal amount) {
        BigDecimal v = amount == null ? ZERO : amount;
        return v.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    // ==================== 校验 ====================

    /**
     * 校验金额是否符合整数位 / 小数位约束（<strong>数值语义</strong>）。
     *
     * <p>典型用法：{@code DECIMAL(18,2)} → {@code isValid(amount, 16, 2)}。</p>
     *
     * <h3>语义说明</h3>
     * <p>本方法采用<strong>数值语义</strong>：先 {@link BigDecimal#stripTrailingZeros()}
     * 得到“有效数值”，再判断有效数值的整数位与小数位是否在约束内。
     * <strong>不检查输入的尾部零</strong>。因此：</p>
     * <ul>
     *   <li>{@code isValid(new BigDecimal("1000.00"), 3, 2)} → {@code false}
     *       （有效数值 1000 需要 4 位整数）</li>
     *   <li>{@code isValid(new BigDecimal("1.00"), 1, 1)} → {@code true}
     *       （有效数值 1 需要 0 位小数）</li>
     *   <li>{@code isValid(new BigDecimal("0.001"), 16, 2)} → {@code false}
     *       （有效数值 0.001 需要 3 位小数，超过 scale=2）</li>
     *   <li>{@code isValid(ZERO, 0, 0)} → {@code true}
     *       （零值恒满足任何整数位 / 小数位约束）</li>
     * </ul>
     *
     * <p>数值语义适用于“数据库列能否容纳该数值”的场景。若需严格拒绝用户输入的
     * 多余尾部零（如 {@code "1.00"} 在 {@code scale=1} 时应被拒），请使用
     * {@link #isValidLiteral(BigDecimal, int, int)}。</p>
     *
     * @param amount        金额；null 视为非法
     * @param integerDigits 允许的最大整数位；必须 &gt;= 0
     * @param scale         允许的最大小数位；必须 &gt;= 0
     * @return 数值语义下是否符合约束
     * @throws IllegalArgumentException if integerDigits or scale is negative
     */
    public static boolean isValid(BigDecimal amount, int integerDigits, int scale) {
        if (integerDigits < 0) {
            throw new IllegalArgumentException("integerDigits 不能为负数: " + integerDigits);
        }
        requireNonNegativeScale(scale);
        if (amount == null) {
            return false;
        }
        // 零值提前返回：有效值 0 需要 0 位整数、0 位小数，恒满足约束。
        // 同时规避 JDK 8u0-8u4 中 ZERO.stripTrailingZeros() 返回 scale>0 的历史 bug
        // （见 JDK-6480539，已在 8u5 修复）。
        if (amount.signum() == 0) {
            return true;
        }
        // stripTrailingZeros 后 scale 可能为负（如 "1000" → 1E+3，scale = -3），
        // 统一按"小数位至少为 0"比较
        BigDecimal n = amount.stripTrailingZeros();
        int actualScale = Math.max(n.scale(), 0);
        if (actualScale > scale) {
            return false;
        }
        // 整数位 = precision - scale；scale 为负意味着末尾有零，整数位相应增加
        // 例：123.45 → precision=5, scale=2 → 3 位整数
        //     1E+3   → precision=1, scale=-3 → 4 位整数
        //     0.001  → precision=1, scale=3  → max(0, -2) = 0 位整数
        int intDigits = Math.max(0, n.precision() - n.scale());
        return intDigits <= integerDigits;
    }

    /**
     * 校验金额是否符合整数位 / 小数位约束（<strong>字面语义</strong>）。
     *
     * <p>与 {@link #isValid(BigDecimal, int, int)} 的区别：本方法<strong>直接检查
     * {@link BigDecimal#scale()} 与 {@link BigDecimal#precision()}</strong>，
     * <strong>不做</strong> {@link BigDecimal#stripTrailingZeros()}。因此：</p>
     * <ul>
     *   <li>{@code isValidLiteral(new BigDecimal("1.00"), 1, 1)} → {@code false}
     *       （字面 scale=2 &gt; 1）</li>
     *   <li>{@code isValidLiteral(new BigDecimal("1.00"), 1, 2)} → {@code true}</li>
     *   <li>{@code isValidLiteral(new BigDecimal("1000.00"), 4, 2)} → {@code true}
     *       （字面 4 位整数 + 2 位小数）</li>
     *   <li>{@code isValidLiteral(new BigDecimal("1000.00"), 3, 2)} → {@code false}
     *       （字面 4 位整数 &gt; 3）</li>
     *   <li>{@code isValidLiteral(new BigDecimal("1E+3"), 4, 0)} → {@code true}
     *       （字面无小数位，数值 1000 需 4 位整数）</li>
     *   <li>{@code isValidLiteral(ZERO, 0, 0)} → {@code false}
     *       （ZERO 的字面 scale=2，超过 0 位小数；对比
     *       {@link #isValid(BigDecimal, int, int) isValid(ZERO, 0, 0)} → {@code true}，
     *       因数值语义下零值恒通过。两者语义不同，选择取决于场景）</li>
     * </ul>
     *
     * <p>字面语义适用于“严格校验用户输入字符串”（如 CSV/Excel 导入、
     * 表单字段长度校验），确保不会因 {@link BigDecimal} 自动去零而放过多余小数位。</p>
     *
     * @param amount        金额；null 视为非法
     * @param integerDigits 允许的最大整数位；必须 &gt;= 0
     * @param scale         允许的最大字面小数位；必须 &gt;= 0
     * @return 字面语义下是否符合约束
     * @throws IllegalArgumentException if integerDigits or scale is negative
     */
    public static boolean isValidLiteral(BigDecimal amount, int integerDigits, int scale) {
        if (integerDigits < 0) {
            throw new IllegalArgumentException("integerDigits 不能为负数: " + integerDigits);
        }
        requireNonNegativeScale(scale);
        if (amount == null) {
            return false;
        }
        if (amount.scale() > scale) {
            return false;
        }
        if (amount.signum() == 0) {
            return true;
        }
        // 整数位 = precision - scale；scale 可能为负（科学计数法输入）
        int intDigits = Math.max(0, amount.precision() - amount.scale());
        return intDigits <= integerDigits;
    }

    // ==================== 内部工具 ====================

    private static void requireNonNegativeScale(int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("scale 不能为负数: " + scale);
        }
    }

    private static void requireNonNull(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为 null");
        }
    }

    /**
     * 去除前后 Unicode 空白；null 原样返回 null。
     *
     * <p>{@link String#trim()} 仅去 {@code <= U+0020} 的字符，无法处理中文业务
     * 常见输入：全角空格 {@code U+3000}、NBSP {@code U+00A0}、em space
     * {@code U+2003} 等。这些字符从 Excel/中文表单/网页复制时极易混入，
     * 若原样传给 {@link BigDecimal#BigDecimal(String)} 会抛 NFE，
     * 与“空白返回零值”的契约相悖。</p>
     *
     * <p>判定见 {@link #isUnicodeBlank(char)}，覆盖 {@code String.trim()}
     * 的全部范围并扩展至 Unicode 空白与空格字符。</p>
     */
    private static String trimUnicode(String s) {
        if (s == null) {
            return null;
        }
        int start = 0;
        int end = s.length();
        while (start < end && isUnicodeBlank(s.charAt(start))) {
            start++;
        }
        while (end > start && isUnicodeBlank(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    /**
     * 是否为“金额输入场景下应去除的空白”。
     *
     * <p>三者并集：</p>
     * <ul>
     *   <li>{@code c <= ' '}：ASCII 控制字符与半角空格，与 {@link String#trim()} 一致；</li>
     *   <li>{@link Character#isWhitespace(char)}：覆盖 {@code U+3000} 全角空格、
     *       {@code U+2003} em space 等；</li>
     *   <li>{@link Character#isSpaceChar(char)}：覆盖 {@code U+00A0} NBSP、
     *       {@code U+2007} figure space、{@code U+202F} narrow NBSP 等
     *       —— 这些字符 {@code isWhitespace} 返回 {@code false}，必须靠此分支兜底。</li>
     * </ul>
     */
    private static boolean isUnicodeBlank(char c) {
        return c <= ' ' || Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    /**
     * 判断 pattern 中是否存在<strong>引号外</strong>的目标字符。
     *
     * <p>DecimalFormat pattern 中单引号内为字面量；连续两个单引号 {@code ''}
     * 表示一个字面量单引号（引号内外语义一致）。本方法跳过引号内内容，
     * 只检查引号外字符，避免误伤 {@code 'EUR'}、{@code '%'}、{@code '约‰'}、
     * {@code 'email'}、{@code '¤'} 等合法字面量。</p>
     *
     * <p>同时校验引号是否闭合：未闭合时抛 IAE，比让 {@link DecimalFormat}
     * 在构造时抛 "Unterminated quote" 更早、消息更明确（异常类型同为 IAE，
     * 语义兼容）。</p>
     *
     * @param pattern 已去前后空白的格式化 pattern
     * @param targets 需在引号外查找的字符
     * @return 引号外是否命中任一目标字符
     * @throws IllegalArgumentException if pattern has an unterminated quote
     */
    private static boolean containsUnquoted(String pattern, char... targets) {
        boolean inQuote = false;
        boolean found = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                // '' 表示一个字面量单引号，引号内外均不切换状态
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && !found) {
                for (char t : targets) {
                    if (c == t) {
                        found = true;
                        break;
                    }
                }
            }
        }
        if (inQuote) {
            throw new IllegalArgumentException("pattern 中单引号未闭合: " + pattern);
        }
        return found;
    }

    /**
     * 取货币标准小数位，供 {@code Currency} 重载使用。
     *
     * <p>{@link Currency#getDefaultFractionDigits()} 对伪货币（{@code XXX} 无货币、
     * {@code XTS} 测试）返回 {@code -1}。若直接透传给 {@code round/zero}，
     * 会触发 {@link #requireNonNegativeScale(int)} 的 IAE，消息为
     * “scale 不能为负数”，对调用方毫无指向性。此处转为可读消息。</p>
     *
     * @throws IllegalArgumentException if currency is null or a pseudo-currency
     */
    private static int requireFractionDigits(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("currency 不能为 null");
        }
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException(
                    "货币 " + currency.getCurrencyCode()
                            + " 无标准小数位（伪货币），请显式指定 scale");
        }
        return digits;
    }

    /**
     * 校验 locale 对应货币的小数位是否为 2。
     *
     * <p>若 locale 无法确定货币（如 {@link Locale#ROOT} 或仅含语言标签），
     * <strong>直接抛 IAE</strong>，不再放行给 NumberFormat 自行推断——
     * 后者的行为依赖 JDK 实现，结果不可控，违反“同源同规则”。</p>
     */
    private static void requireTwoDecimalCurrency(Locale locale) {
        Currency currency;
        try {
            currency = Currency.getInstance(locale);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "locale " + locale + " 无法确定对应货币；本方法仅支持明确的 "
                            + DEFAULT_SCALE + " 位小数货币，请改用 format(BigDecimal, String)", e);
        }
        if (currency.getDefaultFractionDigits() != DEFAULT_SCALE) {
            throw new IllegalArgumentException(
                    "locale " + locale + " 对应货币 " + currency.getCurrencyCode()
                            + " 的小数位为 " + currency.getDefaultFractionDigits()
                            + "，本方法仅支持 " + DEFAULT_SCALE + " 位小数货币；"
                            + "请改用 format(BigDecimal, String)");
        }
    }
}
