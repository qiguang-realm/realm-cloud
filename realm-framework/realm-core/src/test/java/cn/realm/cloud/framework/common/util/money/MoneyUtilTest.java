package cn.realm.cloud.framework.common.util.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MoneyUtil} 单元测试。
 *
 * <h2>断言约定</h2>
 * <ul>
 *   <li>比较数值忽略 scale：{@code assertThat(actual).isEqualByComparingTo("...")}；</li>
 *   <li>比较 scale 也相同：{@code assertThat(actual).isEqualTo(new BigDecimal("..."))}；</li>
 *   <li>格式化输出：因货币符号受 JDK CLDR 数据影响，断言用 {@code contains} / {@code matches}
 *       等宽松匹配，不写死 {@code ¥} / {@code ￥}。</li>
 * </ul>
 */
@DisplayName("MoneyUtil 单元测试")
class MoneyUtilTest {

    // ==================== 常量 ====================

    @Test
    @DisplayName("常量：DEFAULT_SCALE=2, DEFAULT_ROUNDING=HALF_UP, ZERO scale=2, CENT=0.01")
    void constants() {
        assertThat(MoneyUtil.DEFAULT_SCALE).isEqualTo(2);
        assertThat(MoneyUtil.DEFAULT_ROUNDING).isEqualTo(RoundingMode.HALF_UP);
        assertThat(MoneyUtil.ZERO).isEqualTo(new BigDecimal("0.00"));
        assertThat(MoneyUtil.CENT).isEqualByComparingTo("0.01");
    }

    // ==================== 创建 / 转换 ====================

    @Nested
    @DisplayName("of(String)：默认 2 位，HALF_UP")
    class OfTests {

        @Test
        @DisplayName("正常数值 → scale=2")
        void normal() {
            assertThat(MoneyUtil.of("123.456")).isEqualTo(new BigDecimal("123.46"));
            assertThat(MoneyUtil.of("-123.456")).isEqualTo(new BigDecimal("-123.46")); // HALF_UP 远离零
            assertThat(MoneyUtil.of("0")).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("null / 空串 / 纯空白 → ZERO")
        void nullOrBlank() {
            assertThat(MoneyUtil.of(null)).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.of("")).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.of("   ")).isEqualTo(MoneyUtil.ZERO);
        }

        @Test
        @DisplayName("接受科学计数法")
        void scientificNotation() {
            assertThat(MoneyUtil.of("1.23E+3")).isEqualTo(new BigDecimal("1230.00"));
        }

        @Test
        @DisplayName("拒绝分组符与货币符号")
        void rejectGroupingAndSymbol() {
            assertThatThrownBy(() -> MoneyUtil.of("1,234.56")).isInstanceOf(NumberFormatException.class);
            assertThatThrownBy(() -> MoneyUtil.of("¥1234.56")).isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("指定 scale 和 mode")
        void customScaleAndMode() {
            assertThat(MoneyUtil.of("1.235", 2, RoundingMode.HALF_EVEN)).isEqualTo(new BigDecimal("1.24"));
            assertThat(MoneyUtil.of("1.245", 2, RoundingMode.HALF_EVEN)).isEqualTo(new BigDecimal("1.24"));
            assertThat(MoneyUtil.of("1.234", 2, RoundingMode.DOWN)).isEqualTo(new BigDecimal("1.23"));
        }

        @Test
        @DisplayName("scale 为负抛 IAE")
        void negativeScale() {
            assertThatThrownBy(() -> MoneyUtil.of("1", -1, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scale");
        }

        @Test
        @DisplayName("mode 为 null 使用 DEFAULT_ROUNDING")
        void nullMode() {
            assertThat(MoneyUtil.of("1.235", 2, null)).isEqualTo(new BigDecimal("1.24"));
        }
    }

    @Nested
    @DisplayName("ofStrict(String)：拒绝科学计数法")
    class OfStrictTests {

        @ParameterizedTest(name = "拒绝 {0}")
        @ValueSource(strings = {"1.23E+3", "1.23e3", "+123", "1,234.56", "¥1234", "NaN", "Infinity", ".5", "123."})
        void reject(String input) {
            assertThatThrownBy(() -> MoneyUtil.ofStrict(input)).isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("合法数值")
        void accepted() {
            assertThat(MoneyUtil.ofStrict("123.456")).isEqualTo(new BigDecimal("123.46"));
            assertThat(MoneyUtil.ofStrict("-0.01")).isEqualTo(new BigDecimal("-0.01"));
        }

        @Test
        @DisplayName("null / 空白 → ZERO")
        void nullOrBlank() {
            assertThat(MoneyUtil.ofStrict(null)).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.ofStrict("")).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.ofStrict("\u3000")).isEqualTo(MoneyUtil.ZERO);
        }
    }

    @Nested
    @DisplayName("ofYuan / ofCent / nullToZero")
    class ConversionTests {

        @Test
        void ofYuan() {
            assertThat(MoneyUtil.ofYuan(123)).isEqualTo(new BigDecimal("123.00"));
            assertThat(MoneyUtil.ofYuan(-1)).isEqualTo(new BigDecimal("-1.00"));
        }

        @Test
        void ofCent() {
            assertThat(MoneyUtil.ofCent(12345)).isEqualTo(new BigDecimal("123.45"));
        }

        @Test
        @DisplayName("nullToZero：null → ZERO，非 null 原样返回不改变 scale")
        void nullToZero() {
            assertThat(MoneyUtil.nullToZero(null)).isEqualTo(MoneyUtil.ZERO);
            BigDecimal raw = new BigDecimal("1");
            assertThat(MoneyUtil.nullToZero(raw)).isSameAs(raw);
            assertThat(MoneyUtil.nullToZero(raw).scale()).isZero();
        }
    }

    // ==================== 零值 ====================

    @Nested
    @DisplayName("zero 系列")
    class ZeroTests {

        @Test
        void zeroNoArg() {
            assertThat(MoneyUtil.zero()).isSameAs(MoneyUtil.ZERO);
        }

        @Test
        void zeroWithScale() {
            assertThat(MoneyUtil.zero(0)).isEqualTo(new BigDecimal("0"));
            assertThat(MoneyUtil.zero(4)).isEqualTo(new BigDecimal("0.0000"));
        }

        @Test
        @DisplayName("scale 为负抛 IAE")
        void negativeScale() {
            assertThatThrownBy(() -> MoneyUtil.zero(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Currency 版：按货币小数位")
        void zeroCurrency() {
            assertThat(MoneyUtil.zero(Currency.getInstance("JPY"))).isEqualTo(new BigDecimal("0"));
            assertThat(MoneyUtil.zero(Currency.getInstance("BHD"))).isEqualTo(new BigDecimal("0.000"));
        }
    }

    // ==================== 求和 ====================

    @Nested
    @DisplayName("add / sum")
    class SumTests {

        @Test
        @DisplayName("varargs 正常求和")
        void addVarargs() {
            BigDecimal result = MoneyUtil.add(
                    new BigDecimal("1.10"), new BigDecimal("2.20"), new BigDecimal("3.30"));
            assertThat(result).isEqualTo(new BigDecimal("6.60"));
        }

        @Test
        @DisplayName("空数组 → ZERO")
        void addEmpty() {
            assertThat(MoneyUtil.add()).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.add(new BigDecimal[0])).isEqualTo(MoneyUtil.ZERO);
        }

        @Test
        @DisplayName("含 null 元素抛 IAE")
        void addNullElement() {
            assertThatThrownBy(() -> MoneyUtil.add((BigDecimal) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("数组为 null 抛 IAE")
        void addNullArray() {
            assertThatThrownBy(() -> MoneyUtil.add((BigDecimal[]) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("显式 scale")
        void addWithScale() {
            BigDecimal result = MoneyUtil.add(
                    new BigDecimal[]{new BigDecimal("1"), new BigDecimal("2")}, 0);
            assertThat(result).isEqualTo(new BigDecimal("3"));
        }

        @Test
        @DisplayName("集合求和")
        void sumCollection() {
            List<BigDecimal> list = Arrays.asList(new BigDecimal("1.10"), new BigDecimal("2.20"));
            assertThat(MoneyUtil.sum(list)).isEqualTo(new BigDecimal("3.30"));
        }

        @Test
        @DisplayName("空集合 → ZERO")
        void sumEmpty() {
            assertThat(MoneyUtil.sum(Collections.emptyList())).isEqualTo(MoneyUtil.ZERO);
        }

        @Test
        @DisplayName("集合为 null 抛 IAE")
        void sumNullCollection() {
            assertThatThrownBy(() -> MoneyUtil.sum(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("集合含 null 元素抛 IAE")
        void sumNullElement() {
            List<BigDecimal> list = Arrays.asList(new BigDecimal("1"), null);
            assertThatThrownBy(() -> MoneyUtil.sum(list)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== 减 / 乘 / 除 ====================

    @Nested
    @DisplayName("subtract / multiply / divide")
    class ArithmeticTests {

        @Test
        void subtract() {
            assertThat(MoneyUtil.subtract(new BigDecimal("5"), new BigDecimal("3")))
                    .isEqualTo(new BigDecimal("2.00"));
        }

        @Test
        void subtractNullThrows() {
            assertThatThrownBy(() -> MoneyUtil.subtract(null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.subtract(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void multiply() {
            assertThat(MoneyUtil.multiply(new BigDecimal("1.5"), new BigDecimal("2")))
                    .isEqualTo(new BigDecimal("3.00"));
        }

        @Test
        void multiplyNullThrows() {
            assertThatThrownBy(() -> MoneyUtil.multiply(null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void divide() {
            assertThat(MoneyUtil.divide(new BigDecimal("10"), new BigDecimal("3")))
                    .isEqualTo(new BigDecimal("3.33"));
        }

        @Test
        @DisplayName("除数为 0 抛 AE")
        void divideByZero() {
            assertThatThrownBy(() -> MoneyUtil.divide(BigDecimal.ONE, BigDecimal.ZERO))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("null 参数抛 IAE")
        void divideNullThrows() {
            assertThatThrownBy(() -> MoneyUtil.divide(null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.divide(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== 舍入 ====================

    @Nested
    @DisplayName("round")
    class RoundTests {

        @Test
        @DisplayName("默认 2 位 HALF_UP")
        void defaultRound() {
            assertThat(MoneyUtil.round(new BigDecimal("1.235"))).isEqualTo(new BigDecimal("1.24"));
            assertThat(MoneyUtil.round(new BigDecimal("-1.235"))).isEqualTo(new BigDecimal("-1.24"));
        }

        @Test
        @DisplayName("null → 对应 scale 零值")
        void nullRound() {
            assertThat(MoneyUtil.round(null)).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.round(null, 3)).isEqualTo(new BigDecimal("0.000"));
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "1.235, 2, 1.24",   // HALF_UP 进位
                "1.225, 2, 1.23",   // HALF_UP 进位（不是 HALF_EVEN）
                "-1.235, 2, -1.24"  // HALF_UP 对负数远离零
        })
        @DisplayName("HALF_UP 语义")
        void halfUp(String input, int scale, String expected) {
            assertThat(MoneyUtil.round(new BigDecimal(input), scale))
                    .isEqualTo(new BigDecimal(expected));
        }

        @Test
        @DisplayName("Currency 版按货币小数位")
        void roundCurrency() {
            BigDecimal v = new BigDecimal("1.2345");
            assertThat(MoneyUtil.round(v, Currency.getInstance("JPY"))).isEqualTo(new BigDecimal("1"));
            assertThat(MoneyUtil.round(v, Currency.getInstance("CNY"))).isEqualTo(new BigDecimal("1.23"));
            assertThat(MoneyUtil.round(v, Currency.getInstance("BHD"))).isEqualTo(new BigDecimal("1.235"));
        }
    }

    // ==================== 比较 ====================

    @Nested
    @DisplayName("compare / eq / gt / ge / lt / le / max / min / clamp")
    class ComparisonTests {

        @Test
        @DisplayName("eq 忽略 scale")
        void eqIgnoresScale() {
            assertThat(MoneyUtil.eq(new BigDecimal("2.0"), new BigDecimal("2.00"))).isTrue();
            assertThat(MoneyUtil.eq(new BigDecimal("2.0"), new BigDecimal("2.01"))).isFalse();
        }

        @Test
        void comparisons() {
            BigDecimal a = new BigDecimal("1.00");
            BigDecimal b = new BigDecimal("2.00");
            assertThat(MoneyUtil.compare(a, b)).isNegative();
            assertThat(MoneyUtil.gt(b, a)).isTrue();
            assertThat(MoneyUtil.ge(a, new BigDecimal("1.0"))).isTrue();
            assertThat(MoneyUtil.lt(a, b)).isTrue();
            assertThat(MoneyUtil.le(a, a)).isTrue();
        }

        @Test
        void nullThrows() {
            assertThatThrownBy(() -> MoneyUtil.eq(null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.gt(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void maxMin() {
            assertThat(MoneyUtil.max(new BigDecimal("1.0"), new BigDecimal("2.00")))
                    .isEqualByComparingTo("2.00");
            assertThat(MoneyUtil.min(new BigDecimal("1.0"), new BigDecimal("2.00")))
                    .isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("集合版 max / min 拒绝 null / 空")
        void collectionMaxMin() {
            assertThat(MoneyUtil.max(Arrays.asList(new BigDecimal("1"), new BigDecimal("3"))))
                    .isEqualByComparingTo("3");
            assertThat(MoneyUtil.min(Arrays.asList(new BigDecimal("1"), new BigDecimal("3"))))
                    .isEqualByComparingTo("1");

            assertThatThrownBy(() -> MoneyUtil.max((Collection<BigDecimal>) null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.max(Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.max(Arrays.asList(new BigDecimal("1"), null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void clamp() {
            BigDecimal min = new BigDecimal("1");
            BigDecimal max = new BigDecimal("10");
            assertThat(MoneyUtil.clamp(new BigDecimal("5"), min, max))
                    .isEqualByComparingTo("5");
            assertThat(MoneyUtil.clamp(new BigDecimal("0"), min, max))
                    .isEqualByComparingTo("1");
            assertThat(MoneyUtil.clamp(new BigDecimal("20"), min, max))
                    .isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("clamp 下限 > 上限抛 IAE")
        void clampInvalidRange() {
            assertThatThrownBy(() -> MoneyUtil.clamp(BigDecimal.ONE,
                    new BigDecimal("10"), new BigDecimal("1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== 符号 ====================

    @Nested
    @DisplayName("isPositive / isNegative / isZero / isNullOrZero / abs / negate")
    class SignTests {

        @Test
        void signChecks() {
            assertThat(MoneyUtil.isPositive(new BigDecimal("0.01"))).isTrue();
            assertThat(MoneyUtil.isNegative(new BigDecimal("-0.01"))).isTrue();
            assertThat(MoneyUtil.isZero(new BigDecimal("0.00"))).isTrue();
            assertThat(MoneyUtil.isZero(new BigDecimal("-0.00"))).isTrue();
        }

        @Test
        void isNullOrZero() {
            assertThat(MoneyUtil.isNullOrZero(null)).isTrue();
            assertThat(MoneyUtil.isNullOrZero(new BigDecimal("0.00"))).isTrue();
            assertThat(MoneyUtil.isNullOrZero(new BigDecimal("0.01"))).isFalse();
        }

        @Test
        void absNegate() {
            assertThat(MoneyUtil.abs(new BigDecimal("-5.00"))).isEqualByComparingTo("5.00");
            assertThat(MoneyUtil.negate(new BigDecimal("5.00"))).isEqualByComparingTo("-5.00");
        }

        @Test
        void nullThrows() {
            assertThatThrownBy(() -> MoneyUtil.isPositive(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.abs(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.negate(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== 元 / 分 ====================

    @Nested
    @DisplayName("yuanToFen / fenToYuan")
    class YuanFenTests {

        @Test
        void yuanToFen() {
            assertThat(MoneyUtil.yuanToFen(new BigDecimal("12.34"))).isEqualTo(1234);
            assertThat(MoneyUtil.yuanToFen(new BigDecimal("0.005"))).isEqualTo(1);   // HALF_UP
            assertThat(MoneyUtil.yuanToFen(new BigDecimal("-0.005"))).isEqualTo(-1); // 远离零
        }

        @Test
        @DisplayName("HALF_EVEN 负数不走远离零")
        void yuanToFenHalfEven() {
            assertThat(MoneyUtil.yuanToFen(new BigDecimal("-0.005"), RoundingMode.HALF_EVEN)).isZero();
            assertThat(MoneyUtil.yuanToFen(new BigDecimal("-0.015"), RoundingMode.HALF_EVEN))
                    .isEqualTo(-2); // -0.015 → -1.5 分，前一位 1 为奇 → 进位 → -2
        }

        @Test
        void fenToYuan() {
            assertThat(MoneyUtil.fenToYuan(1234)).isEqualTo(new BigDecimal("12.34"));
            assertThat(MoneyUtil.fenToYuan(-1)).isEqualTo(new BigDecimal("-0.01"));
        }

        @Test
        @DisplayName("null 抛 IAE")
        void nullThrows() {
            assertThatThrownBy(() -> MoneyUtil.yuanToFen(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("溢出抛 AE")
        void overflowThrows() {
            assertThatThrownBy(() -> MoneyUtil.yuanToFen(new BigDecimal("99999999999999999999")))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    // ==================== 格式化 ====================

    @Nested
    @DisplayName("format(BigDecimal) / format(BigDecimal, String)")
    class FormatTests {

        @Test
        @DisplayName("默认千分位 2 位")
        void defaultFormat() {
            assertThat(MoneyUtil.format(new BigDecimal("1234567.8"))).isEqualTo("1,234,567.80");
            assertThat(MoneyUtil.format(null)).isEqualTo("0.00");
        }

        @Test
        @DisplayName("自定义 pattern 决定小数位")
        void customPattern() {
            assertThat(MoneyUtil.format(new BigDecimal("1234.567"), "#,##0.000"))
                    .isEqualTo("1,234.567");
            assertThat(MoneyUtil.format(new BigDecimal("1234.5"), "#,##0"))
                    .isEqualTo("1,235"); // 无小数位，按 HALF_UP 舍入
        }

        @Test
        @DisplayName("拒绝引号外的 % / ‰ / E / e / ¤")
        void rejectUnquotedSpecials() {
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "0.00%"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "0.00\u2030"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "0.00E0"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "0.00e0"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "¤#,##0.00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("引号内的 % / E / ¤ 作为字面量放行")
        void allowQuotedLiterals() {
            assertThat(MoneyUtil.format(new BigDecimal("1234.56"), "#,##0.00 '%'"))
                    .isEqualTo("1,234.56 %");
            assertThat(MoneyUtil.format(new BigDecimal("1234.56"), "#,##0.00 'EUR'"))
                    .isEqualTo("1,234.56 EUR");
            // DecimalFormat 对不含数字占位符的 pattern 会自动补一个 '#'，
            // 因此这里显式写 0 占位符，断言与"字面量"语义直接对应。
            assertThat(MoneyUtil.format(BigDecimal.ONE, "0'¤'"))
                    .isEqualTo("1¤");
            assertThat(MoneyUtil.format(BigDecimal.ONE, "0''"))
                    .isEqualTo("1'");
        }

        @Test
        @DisplayName("未闭合引号抛 IAE")
        void unterminatedQuote() {
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "#,##0.00 'EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("引号");
        }

        @Test
        @DisplayName("pattern 为 null / 空白抛 IAE")
        void blankPattern() {
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.format(BigDecimal.ONE, "\u3000"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("formatWithSymbol 系列")
    class FormatWithSymbolTests {

        @Test
        @DisplayName("无参：人民币符号 + 2 位小数")
        void defaultSymbol() {
            String s = MoneyUtil.formatWithSymbol(new BigDecimal("1234.56"));
            assertThat(s).contains("1,234.56");
            assertThat(s).matches(".*[¥￥].*"); // 符号随 CLDR 不同
        }

        @Test
        @DisplayName("Locale 版：仅 2 位货币")
        void localeVersion() {
            assertThat(MoneyUtil.formatWithSymbol(new BigDecimal("1234.56"), Locale.CHINA))
                    .contains("1,234.56");
            assertThatThrownBy(() -> MoneyUtil.formatWithSymbol(BigDecimal.ONE, Locale.JAPAN))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.formatWithSymbol(BigDecimal.ONE, Locale.ROOT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Currency 版：支持非 2 位货币")
        void currencyVersion() {
            // JPY：0 位小数
            String jpy = MoneyUtil.formatWithSymbol(new BigDecimal("1234.56"), Currency.getInstance("JPY"));
            assertThat(jpy).contains("1,235");
            assertThat(jpy).doesNotContain(".");

            // BHD：3 位小数
            String bhd = MoneyUtil.formatWithSymbol(new BigDecimal("1234.5678"), Currency.getInstance("BHD"));
            assertThat(bhd).contains("1,234.568");
        }

        @Test
        @DisplayName("伪货币 / null currency 抛 IAE")
        void invalidCurrency() {
            assertThatThrownBy(() ->
                    MoneyUtil.formatWithSymbol(BigDecimal.ONE, Currency.getInstance("XXX")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("伪货币");
            assertThatThrownBy(() ->
                    MoneyUtil.formatWithSymbol(BigDecimal.ONE, (Currency) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null amount 按货币 scale 生成零值")
        void nullAmount() {
            assertThat(MoneyUtil.formatWithSymbol(null, Currency.getInstance("JPY")))
                    .contains("0");
            assertThat(MoneyUtil.formatWithSymbol(null, Currency.getInstance("BHD")))
                    .contains("0.000");
        }
    }

    @Nested
    @DisplayName("toPlainString")
    class ToPlainStringTests {

        @Test
        @DisplayName("默认 scale=2，无科学计数法")
        void defaultVersion() {
            assertThat(MoneyUtil.toPlainString(new BigDecimal("1E+3"))).isEqualTo("1000.00");
            assertThat(MoneyUtil.toPlainString(new BigDecimal("1234.56"))).isEqualTo("1234.56");
        }

        @Test
        @DisplayName("指定 scale")
        void customScale() {
            assertThat(MoneyUtil.toPlainString(new BigDecimal("1.235"), 1)).isEqualTo("1.2");
            assertThat(MoneyUtil.toPlainString(new BigDecimal("1.25"), 1)).isEqualTo("1.3");
        }

        @Test
        @DisplayName("指定 mode")
        void customMode() {
            assertThat(MoneyUtil.toPlainString(new BigDecimal("1.235"), 2, RoundingMode.HALF_EVEN))
                    .isEqualTo("1.24");
        }

        @Test
        void nullThrows() {
            assertThatThrownBy(() -> MoneyUtil.toPlainString(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== 校验 ====================

    @Nested
    @DisplayName("isValid（数值语义）")
    class IsValidTests {

        @ParameterizedTest(name = "{0} / integerDigits={1}, scale={2} → {3}")
        @CsvSource({
                "1000.00, 3, 2, false",   // 有效值 1000 需 4 位整数
                "1000.00, 4, 2, true",
                "1.00,   1, 1, true",     // 有效值 1 需 0 位小数
                "0.001, 16, 2, false",    // 需要 3 位小数
                "0.00,   0, 0, true",     // 零值恒通过
                "-1.5,   1, 1, true"
        })
        void cases(String amount, int integerDigits, int scale, boolean expected) {
            assertThat(MoneyUtil.isValid(new BigDecimal(amount), integerDigits, scale))
                    .isEqualTo(expected);
        }

        @Test
        void nullReturnsFalse() {
            assertThat(MoneyUtil.isValid(null, 10, 2)).isFalse();
        }

        @Test
        void negativeArgsThrow() {
            assertThatThrownBy(() -> MoneyUtil.isValid(BigDecimal.ONE, -1, 2))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.isValid(BigDecimal.ONE, 10, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isValidLiteral（字面语义）")
    class IsValidLiteralTests {

        @ParameterizedTest(name = "{0} / integerDigits={1}, scale={2} → {3}")
        @CsvSource({
                "1.00,   1, 1, false",    // 字面 scale=2
                "1.00,   1, 2, true",
                "1000.00, 4, 2, true",
                "1000.00, 3, 2, false",
                "1E+3,   4, 0, true",
                "0.00,   0, 0, false"     // ZERO 字面 scale=2
        })
        void cases(String amount, int integerDigits, int scale, boolean expected) {
            assertThat(MoneyUtil.isValidLiteral(new BigDecimal(amount), integerDigits, scale))
                    .isEqualTo(expected);
        }

        @Test
        void nullReturnsFalse() {
            assertThat(MoneyUtil.isValidLiteral(null, 10, 2)).isFalse();
        }
    }

    // ==================== Unicode 空白 ====================

    @Nested
    @DisplayName("Unicode 空白处理")
    class UnicodeWhitespaceTests {

        @ParameterizedTest(name = "of(\"{0}\") → 1234.56")
        @ValueSource(strings = {
                "1234.56\u3000",       // 全角空格
                "\u30001234.56",       // 全角空格（前）
                "\u00A01234.56\u00A0", // NBSP
                "  1234.56\u2003",     // em space
                "1234.56\u202F"        // narrow NBSP
        })
        void ofTrimUnicode(String input) {
            assertThat(MoneyUtil.of(input)).isEqualTo(new BigDecimal("1234.56"));
        }

        @ParameterizedTest(name = "纯空白 \"{0}\" → ZERO")
        @ValueSource(strings = {"\u3000", "\u00A0", "\u2003\u2003", "\u3000\u00A0"})
        void blankInputReturnsZero(String input) {
            assertThat(MoneyUtil.of(input)).isEqualTo(MoneyUtil.ZERO);
            assertThat(MoneyUtil.ofStrict(input)).isEqualTo(MoneyUtil.ZERO);
        }

        @Test
        @DisplayName("ofStrict 也 trim Unicode 空白后再校验")
        void ofStrictTrimUnicode() {
            assertThat(MoneyUtil.ofStrict("\u3000123.45\u3000"))
                    .isEqualTo(new BigDecimal("123.45"));
            // 拒绝 Unicode 空白包裹的科学计数法
            assertThatThrownBy(() -> MoneyUtil.ofStrict("\u30001E+3\u3000"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("format 的 pattern 也 trim Unicode 空白")
        void formatTrimPattern() {
            assertThat(MoneyUtil.format(new BigDecimal("1234.5"), "\u3000#,##0.00\u3000"))
                    .isEqualTo("1,234.50");
        }
    }

    // ==================== Currency 便捷重载 ====================

    @Nested
    @DisplayName("Currency 便捷重载")
    class CurrencyTests {

        @Test
        void ofWithCurrency() {
            assertThat(MoneyUtil.of("1234.56", Currency.getInstance("JPY")))
                    .isEqualTo(new BigDecimal("1235")); // JPY 0 位
            assertThat(MoneyUtil.of("1234.5678", Currency.getInstance("BHD")))
                    .isEqualTo(new BigDecimal("1234.568")); // BHD 3 位
        }

        @Test
        void ofStrictWithCurrency() {
            assertThat(MoneyUtil.ofStrict("1234.56", Currency.getInstance("CNY")))
                    .isEqualTo(new BigDecimal("1234.56"));
            assertThatThrownBy(() ->
                    MoneyUtil.ofStrict("1E+3", Currency.getInstance("CNY")))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("null currency 抛 IAE")
        void nullCurrency() {
            assertThatThrownBy(() -> MoneyUtil.of("1", (Currency) null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.zero((Currency) null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MoneyUtil.round(BigDecimal.ONE, (Currency) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("伪货币抛 IAE 且消息可读")
        void pseudoCurrency() {
            assertThatThrownBy(() -> MoneyUtil.of("1", Currency.getInstance("XXX")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("伪货币");
            assertThatThrownBy(() -> MoneyUtil.zero(Currency.getInstance("XTS")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("伪货币");
        }

        @Test
        @DisplayName("null value 但有效货币 → 返回对应 scale 零值")
        void nullValueValidCurrency() {
            assertThat(MoneyUtil.of(null, Currency.getInstance("JPY")))
                    .isEqualTo(new BigDecimal("0"));
            assertThat(MoneyUtil.ofStrict(null, Currency.getInstance("BHD")))
                    .isEqualTo(new BigDecimal("0.000"));
        }
    }
}
