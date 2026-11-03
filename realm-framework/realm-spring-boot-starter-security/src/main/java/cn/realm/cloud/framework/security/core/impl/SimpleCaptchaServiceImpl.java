package cn.realm.cloud.framework.security.core.impl;

import cn.realm.cloud.framework.security.core.CaptchaService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * 简单验证码实现：生成 SVG 形式验证码，缓存 key → code 映射。
 *
 * <p>依据：NIST SP 800-63B-4 要求认证端点具备暴力破解防护。
 *
 * @author QI Guang
 */
public class SimpleCaptchaServiceImpl implements CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;
    private static final Duration TTL = Duration.ofMinutes(2);

    private final Cache<String, String> cache;
    private final Random random = new Random();

    public SimpleCaptchaServiceImpl() {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public CaptchaResult generate() {
        String code = randomCode();
        String key = UUID.randomUUID().toString().replace("-", "");
        cache.put(key, code);
        return new CaptchaResult(key, renderSvg(code));
    }

    @Override
    public void verify(String key, String code) {
        if (key == null || code == null) {
            throw new BadCredentialsException("验证码不能为空");
        }
        String expected = cache.getIfPresent(key);
        cache.invalidate(key);
        if (expected == null || !expected.equalsIgnoreCase(code)) {
            throw new BadCredentialsException("验证码错误或已过期");
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String renderSvg(String code) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='40'>"
                + "<rect width='100%' height='100%' fill='#f2f2f2'/>"
                + "<text x='10' y='28' font-size='24' font-family='monospace' fill='#333'>"
                + code + "</text></svg>";
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
