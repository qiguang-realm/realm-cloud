/**
 * Realm 模块化单体（Modular Monolith）的根包。
 *
 * <p><b>模块化规则（Spring Modulith）：</b>
 * <ul>
 *     <li>本包是 Spring Modulith 的“根包（Root Package）”。</li>
 *     <li>本包下的每一个直接子包（如 {@code customer}, {@code payment}, {@code workforce}）
 *         都会被自动识别为一个独立的业务模块。</li>
 *     <li><b>严禁</b>将 {@code ServerApplication} 下沉到任何子包（如 {@code cn.realm.cloud.server}），
 *         否则 Modulith 会将子包误认为根包，导致所有业务模块无法被正确加载和验证。</li>
 * </ul>
 *
 * <p><b>架构红线：</b>
 * 本包及 {@code server} 子包下禁止编写任何业务逻辑（Controller、Service、Repository）。
 * 业务逻辑必须归属于各自的业务模块（位于 {@code cn.realm.cloud.*}）。
 *
 * <p><b>Module Positioning:</b>
 * The root package of the modular monolith. Each direct sub-package is detected
 * as an independent business module by Spring Modulith. The {@code ServerApplication}
 * must reside directly in this package. No business logic is allowed here.
 *
 * @author QI Guang
 */
package cn.realm.cloud;
