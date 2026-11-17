/**
 * Realm 框架的测试支撑模块。
 * <p>
 * 本模块负责聚合测试依赖（JUnit 5, AssertJ, Mockito, ArchUnit, Spring Modulith Test 等），
 * 并提供统一的测试基类、架构断言和 Mock 工具。
 * <p>
 * <b>架构红线（严禁跨越）：</b>
 * <ul>
 *     <li><b>禁止生产污染</b>：绝对不包含任何 {@code src/main} 下的生产级自动配置。
 *         下游模块必须以 {@code <scope>test</scope>} 引入本模块，严禁进入生产类路径。</li>
 *     <li><b>零业务语义</b>：禁止包含任何具体业务（如用户、订单）的实体或 Fixture。</li>
 *     <li><b>无状态</b>：测试基类和工具类不应持有跨测试用例的可变状态。</li>
 * </ul>
 */
package cn.realm.cloud.framework.test;
