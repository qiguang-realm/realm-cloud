package cn.realm.cloud.workforce.identity.internal.captcha.infrastructure;

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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li><b>生成（get）</b>：生成随机文本 → 绘制图片 → 缓存文本+密钥 → 返回 Base64 + token</li>
 *   <li><b>校验（check）</b>：用户输入文本 → 与缓存比对 → 成功则生成二次校验凭证</li>
 *   <li><b>二次校验（verification）</b>：最终确认凭证有效 → 删除凭证（防重放）</li>
 * </ul>
 *
 * @author QI Guang
 */
@Component
@Primary
@RequiredArgsConstructor // 推荐使用构造器注入
public class PictureWordCaptchaServiceImpl extends AbstractCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(PictureWordCaptchaServiceImpl.class);

    // ==================== 常量定义 ====================
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;
    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 40;
    private static final int INTERFERENCE_LINES = 10;
    private static final int NOISE_POINTS = 100;
    private static final int FONT_SIZE = 24;
    private static final int CHAR_SPACING = 20;
    private static final int CHAR_BASE_Y = 24;
    private static final int CHAR_Y_RANDOM_RANGE = 8;
    private static final int ROTATION_RANGE_DEGREES = 45;

    // 预编译正则，减少 GC 压力
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\r|\n");

    // ==================== 依赖注入 ====================
    private final CaptchaCacheService captchaCacheService;
    private final AjCaptchaProperties ajCaptchaProperties;

    // ==================== 生命周期 ====================

    @PostConstruct
    public void initBean() {
        // 防御性设置：确保在无图形界面的 Linux 服务器上正常绘制图片
        System.setProperty("java.awt.headless", "true");

        // 1. 将 YAML 配置转换为 Properties
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

        // 3. 构造器注入已保证 captchaCacheService 一定不为空，直接打印日志即可
        log.info("图片文字验证码服务初始化完成，缓存类型：{}，水印：{}", cacheType, waterMark);
    }

    @Override
    public void init(Properties config) {
        // 所有初始化已移至 initBean，此处留空防外部调用覆盖
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
        // 使用 Java 原生 API 替代 Apache Commons Lang3
        if (userInput == null || userInput.isBlank()) {
            log.warn("用户输入为空，token: {}", captchaVO.getToken());
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR);
        }

        String trimmedInput = userInput.trim();
        // 忽略大小写比对
        if (!correctCode.equalsIgnoreCase(trimmedInput)) {
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

        String imageBase64 = LINE_BREAK_PATTERN.matcher(ImageUtils.getImageToBase64Str(image)).replaceAll("");
        dataVO.setOriginalImageBase64(imageBase64);

        String codeKey = String.format(REDIS_CAPTCHA_KEY, dataVO.getToken());
        captchaCacheService.set(codeKey, buildCodeValue(text, secretKey), EXPIRESIN_SECONDS);

        return dataVO;
    }

    private BufferedImage generateCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            g.setColor(getRandomColor(200, 250));
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            for (int i = 0; i < INTERFERENCE_LINES; i++) {
                g.setColor(getRandomColor(100, 200));
                g.drawLine(
                        RandomUtil.randomInt(IMAGE_WIDTH),
                        RandomUtil.randomInt(IMAGE_HEIGHT),
                        RandomUtil.randomInt(IMAGE_WIDTH),
                        RandomUtil.randomInt(IMAGE_HEIGHT)
                );
            }

            g.setFont(new Font("Arial", Font.BOLD, FONT_SIZE));

            char[] chars = text.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                g.setColor(getRandomColor(20, 130));

                int x = 20 + i * CHAR_SPACING;
                int y = CHAR_BASE_Y + RandomUtil.randomInt(CHAR_Y_RANDOM_RANGE);

                AffineTransform transform = new AffineTransform();
                double angle = Math.toRadians(RandomUtil.randomInt(-ROTATION_RANGE_DEGREES, ROTATION_RANGE_DEGREES));
                transform.setToRotation(angle, x, y);
                g.setTransform(transform);

                g.drawString(String.valueOf(chars[i]), x, y);
            }

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
     * 重写父类方法，确保父类内部逻辑（如限流计数器）使用 Spring 注入的缓存服务。
     * 这样框架底层无论何时请求缓存，都会拿到 Spring 容器中的同一实例。
     */
    @Override
    protected CaptchaCacheService getCacheService(String cacheType) {
        return this.captchaCacheService;
    }
}
