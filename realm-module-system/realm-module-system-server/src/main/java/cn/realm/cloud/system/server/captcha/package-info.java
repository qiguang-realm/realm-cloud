/**
 * 验证码模块。
 *
 * <p>提供图形验证码的获取与校验能力，服务于登录、注册、找回密码等无需登录即可访问的流程。
 * 模块只包含 HTTP 入口、AJ-Captcha 的 SPI 实现与装配配置，不依赖任何业务模块，
 * 因此处于依赖图的叶子位置，不会与 {@code user}、{@code role} 等模块形成循环。
 *
 * <p>当前其他模块与验证码的交互仅通过 HTTP 端点完成，不存在编译期依赖。
 * 如未来需要在服务端程序化校验验证码（例如登录逻辑内联校验而非前端先调 {@code /check}），
 * 再在根包新增 {@code CaptchaFacade} 暴露最小接口，并在此处补充对应的依赖说明。
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared"}
)
package cn.realm.cloud.system.server.captcha;
