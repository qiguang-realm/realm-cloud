package cn.realm.cloud.system.server.infra.captcha;

import cn.hutool.core.util.RandomUtil;
import com.anji.captcha.model.common.RepCodeEnum;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.properties.AjCaptchaProperties;
import com.anji.captcha.service.CaptchaCacheService;
import com.anji.captcha.service.impl.AbstractCaptchaService;
import com.anji.captcha.util.AESUtil;
import com.anji.captcha.util.ImageUtils;
import com.anji.captcha.util.RandomUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 图片文字验证码服务实现
 *
 * <p><b>验证码类型：</b>图片中显示随机字符串，用户输入该字符串进行校验。
 * 区别于滑块拼图或文字点选，更接近传统图形验证码，但增加了 AES 加密和二次校验。
 *
 * <p><b>前端调用约定（重要）：</b>
 * AJ-Captcha 未为文本输入预留专用字段，本实现复用 {@link CaptchaVO#getPointJson()} 作为用户输入的文本。
 * 前端在调用 check 接口时，需将用户输入的验证码字符串放入 pointJson 字段中。
 *
 * <p><b>核心流程：</b>
 * <ul>
 *   <li><b>生成（get）</b>：生成随机文本 → 绘制图片（干扰线 + 旋转文字 + 噪点）→ 缓存文本+密钥 → 返回 Base64 + token</li>
 *   <li><b>校验（check）</b>：用户输入文本 → 与缓存比对 → 成功则生成二次校验凭证（加密）</li>
 *   <li><b>二次校验（verification）</b>：最终确认凭证有效 → 删除凭证（防重放）</li>
 * </ul>
 *
 * @author QI Guang
 */
@Component
@Primary
public class PictureWordCaptchaServiceImpl extends AbstractCaptchaService {

    // ==================== 常量定义 ====================

    /**
     * 验证码可用字符集（排除 0、O、I、1 等易混淆字符）
     */
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /**
     * 验证码长度（字符数）
     */
    private static final int CODE_LENGTH = 4;
    /**
     * 图片宽度（像素）
     */
    private static final int IMAGE_WIDTH = 120;
    /**
     * 图片高度（像素）
     */
    private static final int IMAGE_HEIGHT = 40;
    /**
     * 干扰线数量
     */
    private static final int INTERFERENCE_LINES = 10;
    /**
     * 噪点数量
     */
    private static final int NOISE_POINTS = 100;
    /**
     * 字体大小（建议与 CHAR_BASE_Y 联动调整）
     */
    private static final int FONT_SIZE = 24;
    /**
     * 单个字符水平间距（像素）
     */
    private static final int CHAR_SPACING = 20;
    /**
     * 字符 Y 轴基础偏移（建议与 FONT_SIZE 一致）
     */
    private static final int CHAR_BASE_Y = 24;
    /**
     * 字符 Y 轴随机偏移范围（像素）
     */
    private static final int CHAR_Y_RANDOM_RANGE = 8;
    /**
     * 字符旋转角度范围（度）
     */
    private static final int ROTATION_RANGE_DEGREES = 45;
    /**
     * 换行符正则（预编译，避免重复创建）
     */
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\r|\n");

    private static final Logger log = LoggerFactory.getLogger(PictureWordCaptchaServiceImpl.class);

    // ==================== 依赖注入 ====================

    @Autowired
    private CaptchaCacheService captchaCacheService;

    @Autowired
    private AjCaptchaProperties ajCaptchaProperties;

    // ==================== 生命周期 ====================

    /**
     * Bean 创建后自动初始化（替代父类的 init 方法）
     * <p>
     * 职责：
     * 1. 从 AjCaptchaProperties 构建 Properties，调用父类 init 设置静态常量
     * 2. 确保缓存服务已注入（若为空则从工厂获取兜底）
     * 3. 记录初始化日志
     */
    @PostConstruct
    public void initBean() {
        // 1. 将 YAML 配置转换为 Properties（父类 init 需要）
        Properties props = new Properties();
        props.setProperty("captcha.cacheType", ajCaptchaProperties.getCacheType().name());
        props.setProperty("captcha.water.mark", ajCaptchaProperties.getWaterMark());
        props.setProperty("captcha.slip.offset", ajCaptchaProperties.getSlipOffset());
        props.setProperty("captcha.aes.status", String.valueOf(ajCaptchaProperties.getAesStatus()));
        props.setProperty("captcha.interference.options", ajCaptchaProperties.getInterferenceOptions());
        props.setProperty("captcha.cache.number", ajCaptchaProperties.getCacheNumber());
        props.setProperty("captcha.timing.clear", ajCaptchaProperties.getTimingClear());
        props.setProperty("captcha.history.data.clear.enable", String.valueOf(ajCaptchaProperties.isHistoryDataClearEnable()));
        props.setProperty("captcha.req.frequency.limit.enable", String.valueOf(ajCaptchaProperties.isReqFrequencyLimitEnable()));
        props.setProperty("captcha.req.get.lock.limit", String.valueOf(ajCaptchaProperties.getReqGetLockLimit()));
        props.setProperty("captcha.req.get.lock.seconds", String.valueOf(ajCaptchaProperties.getReqGetLockSeconds()));
        props.setProperty("captcha.req.get.minute.limit", String.valueOf(ajCaptchaProperties.getReqGetMinuteLimit()));
        props.setProperty("captcha.req.check.minute.limit", String.valueOf(ajCaptchaProperties.getReqCheckMinuteLimit()));
        props.setProperty("captcha.req.verify.minute.limit", String.valueOf(ajCaptchaProperties.getReqVerifyMinuteLimit()));

        // 2. 调用父类 init（设置 REDIS_CAPTCHA_KEY、EXPIRESIN_SECONDS 等静态常量）
        super.init(props);

        // 3. 确保缓存服务已注入（若 @Autowired 失败，从工厂兜底）
        if (this.captchaCacheService == null) {
            log.warn("缓存服务未注入，从工厂获取兜底");
            this.captchaCacheService = super.getCacheService(cacheType);
        }
        if (this.captchaCacheService == null) {
            throw new IllegalStateException("验证码缓存服务初始化失败，cacheType: " + cacheType);
        }

        log.info("图片文字验证码服务初始化完成，缓存类型：{}，水印：{}", cacheType, waterMark);
    }

    /**
     * 父类 init 方法的重写（保留空实现，避免被外部手动调用干扰）
     * 实际初始化由 @PostConstruct 完成
     */
    @Override
    public void init(Properties config) {
        // 所有初始化已移至 initBean，此处留空
        log.debug("PictureWordCaptchaServiceImpl.init(Properties) 被调用，但实际初始化由 @PostConstruct 完成");
    }

    @Override
    public void destroy(Properties config) {
        log.info("图片文字验证码服务销毁，开始清理历史数据...");
    }

    @Override
    public String captchaType() {
        return "pictureWord";
    }

    // ==================== 核心业务方法 ====================

    @Override
    public ResponseModel get(CaptchaVO captchaVO) {
        String text = generateRandomText(CODE_LENGTH);
        CaptchaVO imageData = buildImageData(text);
        log.debug("生成验证码，token: {}", imageData.getToken());
        return ResponseModel.successData(imageData);
    }

    @Override
    public ResponseModel check(CaptchaVO captchaVO) {
        ResponseModel r = super.check(captchaVO);
        if (!validatedReq(r)) {
            return r;
        }

        // 1. 从缓存获取正确文本和密钥
        String codeKey = String.format(REDIS_CAPTCHA_KEY, captchaVO.getToken());
        if (!captchaCacheService.exists(codeKey)) {
            log.warn("验证码已过期或不存在，token: {}", captchaVO.getToken());
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID);
        }

        String codeValue = captchaCacheService.get(codeKey);
        captchaCacheService.delete(codeKey); // 一次性验证码，用完即删（防重放）

        String correctCode = extractCodeFromValue(codeValue);
        String secretKey = extractSecretKeyFromValue(codeValue);

        // 2. 获取用户输入（复用 pointJson 字段）
        String userInput = captchaVO.getPointJson();
        if (StringUtils.isBlank(userInput)) {
            log.warn("用户输入为空，token: {}", captchaVO.getToken());
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR);
        }

        String trimmedInput = userInput.trim();
        if (!StringUtils.equalsIgnoreCase(correctCode, trimmedInput)) {
            log.warn("验证码匹配失败，token: {}, 输入: {}, 正确: {}", captchaVO.getToken(), trimmedInput, correctCode);
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR);
        }

        // 3. 校验通过，生成二次校验凭证
        try {
            String combined = captchaVO.getToken().concat("---").concat(trimmedInput);
            String encrypted = AESUtil.aesEncrypt(combined, secretKey);
            String secondKey = String.format(REDIS_SECOND_CAPTCHA_KEY, encrypted);
            captchaCacheService.set(secondKey, captchaVO.getToken(), EXPIRESIN_THREE);

            captchaVO.setResult(true);
            captchaVO.resetClientFlag();
            log.debug("验证码校验成功，token: {}", captchaVO.getToken());
            return ResponseModel.successData(captchaVO);
        } catch (Exception e) {
            log.error("AES 加密失败，token: {}", captchaVO.getToken(), e);
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg("加密失败，请稍后重试");
        }
    }

    @Override
    public ResponseModel verification(CaptchaVO captchaVO) {
        ResponseModel r = super.verification(captchaVO);
        if (!validatedReq(r)) {
            return r;
        }

        try {
            String secondKey = String.format(REDIS_SECOND_CAPTCHA_KEY, captchaVO.getCaptchaVerification());
            if (!captchaCacheService.exists(secondKey)) {
                log.warn("二次校验凭证无效或已过期，token: {}", captchaVO.getToken());
                return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID);
            }
            captchaCacheService.delete(secondKey);
            log.debug("二次校验成功，token: {}", captchaVO.getToken());
            return ResponseModel.success();
        } catch (Exception e) {
            log.error("二次校验异常，token: {}", captchaVO.getToken(), e);
            return ResponseModel.errorMsg("二次校验失败");
        }
    }

    // ==================== 图片生成辅助方法 ====================

    private CaptchaVO buildImageData(String text) {
        BufferedImage image = generateCaptchaImage(text);

        // 生成 AES 密钥（若启用加密）
        String secretKey = null;
        if (captchaAesStatus) {
            try {
                secretKey = AESUtil.getKey();
            } catch (Exception e) {
                log.error("生成 AES 密钥失败", e);
            }
        }

        CaptchaVO dataVO = new CaptchaVO();
        dataVO.setToken(RandomUtils.getUUID());
        dataVO.setSecretKey(secretKey);
        // 使用预编译正则，减少 GC 压力
        String imageBase64 = LINE_BREAK_PATTERN.matcher(ImageUtils.getImageToBase64Str(image)).replaceAll("");
        dataVO.setOriginalImageBase64(imageBase64);

        // 缓存文本和密钥
        String codeKey = String.format(REDIS_CAPTCHA_KEY, dataVO.getToken());
        captchaCacheService.set(codeKey, buildCodeValue(text, secretKey), EXPIRESIN_SECONDS);

        return dataVO;
    }

    private BufferedImage generateCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // 1. 背景色（浅色）
            g.setColor(getRandomColor(200, 250));
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // 2. 干扰线
            for (int i = 0; i < INTERFERENCE_LINES; i++) {
                g.setColor(getRandomColor(100, 200));
                g.drawLine(
                        RandomUtil.randomInt(IMAGE_WIDTH),
                        RandomUtil.randomInt(IMAGE_HEIGHT),
                        RandomUtil.randomInt(IMAGE_WIDTH),
                        RandomUtil.randomInt(IMAGE_HEIGHT)
                );
            }

            // 3. 字体
            g.setFont(new Font("Arial", Font.BOLD, FONT_SIZE));

            // 4. 逐个绘制字符（带旋转 + 随机偏移）
            char[] chars = text.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                g.setColor(getRandomColor(20, 130));

                int x = 20 + i * CHAR_SPACING;
                int y = CHAR_BASE_Y + RandomUtil.randomInt(CHAR_Y_RANDOM_RANGE);

                // 旋转变换（-45° ~ 45°）
                AffineTransform transform = new AffineTransform();
                double angle = Math.toRadians(RandomUtil.randomInt(-ROTATION_RANGE_DEGREES, ROTATION_RANGE_DEGREES));
                transform.setToRotation(angle, x, y);
                g.setTransform(transform);

                g.drawString(String.valueOf(chars[i]), x, y);
            }

            // 5. 噪点
            for (int i = 0; i < NOISE_POINTS; i++) {
                image.setRGB(
                        RandomUtil.randomInt(IMAGE_WIDTH),
                        RandomUtil.randomInt(IMAGE_HEIGHT),
                        getRandomColor(0, 255).getRGB()
                );
            }
        } finally {
            g.dispose();
        }

        return image;
    }

    private Color getRandomColor(int min, int max) {
        int minVal = Math.min(min, max);
        int maxVal = Math.max(min, max);
        return new Color(
                RandomUtil.randomInt(minVal, maxVal),
                RandomUtil.randomInt(minVal, maxVal),
                RandomUtil.randomInt(minVal, maxVal)
        );
    }

    /**
     * 生成随机验证码文本（protected 便于单元测试覆写）
     */
    protected String generateRandomText(int length) {
        return RandomUtil.randomString(CHARACTERS, length);
    }

    // ==================== 缓存数据编解码 ====================

    private String buildCodeValue(String text, String secretKey) {
        return text + "," + (secretKey != null ? secretKey : "");
    }

    private String extractCodeFromValue(String codeValue) {
        if (codeValue == null) {
            return "";
        }
        String[] parts = codeValue.split(",", 2);
        return parts.length > 0 ? parts[0] : "";
    }

    private String extractSecretKeyFromValue(String codeValue) {
        if (codeValue == null) {
            return "";
        }
        String[] parts = codeValue.split(",", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    // ==================== 父类方法覆写 ====================

    /**
     * 重写父类方法，确保父类内部逻辑（如限流计数器）使用 Spring 注入的缓存服务
     *
     * @param cacheType 缓存类型标识（本实现中忽略，统一返回注入实例）
     * @return Spring 管理的 CaptchaCacheService 实例，若未注入则回退父类工厂
     */
    @Override
    protected CaptchaCacheService getCacheService(String cacheType) {
        return this.captchaCacheService != null ? this.captchaCacheService : super.getCacheService(cacheType);
    }
}
