package cn.realm.cloud.system.server.infra.captcha;

import com.anji.captcha.service.CaptchaCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * AJ-Captcha 验证码的 Redis 缓存服务实现
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>作为 AJ-Captcha 的 SPI 缓存适配器，将验证码数据存储于 Redis 中</li>
 *   <li>支持分布式环境下的验证码状态共享</li>
 * </ul>
 *
 * @author QI Guang
 */
@Slf4j
public class RedisCaptchaServiceImpl implements CaptchaCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisCaptchaServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public void set(String key, String value, long expiresInSeconds) {
        try {
            if (expiresInSeconds > 0) {
                stringRedisTemplate.opsForValue().set(key, value, expiresInSeconds, TimeUnit.SECONDS);
            } else {
                stringRedisTemplate.opsForValue().set(key, value);
                log.warn("[验证码缓存] 过期时间 <= 0，key: {} 将永久存储", key);
            }
            log.debug("[验证码缓存] 写入成功, key: {}, expire: {}s", key, expiresInSeconds);
        } catch (Exception e) {
            log.error("[验证码缓存] Redis set 失败, key: {}", key, e);
            throw new RuntimeException("验证码缓存写入失败，请检查 Redis 连接", e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("[验证码缓存] Redis exists 失败, key: {}", key, e);
            return false; // 降级：视为不存在，触发重新获取
        }
    }

    @Override
    public void delete(String key) {
        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("[验证码缓存] 删除成功, key: {}", key);
            } else {
                log.warn("[验证码缓存] 删除失败或 key 不存在, key: {}", key);
            }
        } catch (Exception e) {
            log.error("[验证码缓存] Redis delete 异常, key: {}", key, e);
        }
    }

    @Override
    public String get(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                log.debug("[验证码缓存] key 不存在或已过期, key: {}", key);
            }
            return value;
        } catch (Exception e) {
            log.error("[验证码缓存] Redis get 失败, key: {}", key, e);
            return null; // 降级：返回 null，上层判定为失效
        }
    }

    /**
     * 【核心】缓存续期（防止验证码在拖拽过程中过期）
     */
    @Override
    public void setExpire(String key, long l) {
        if (l <= 0) {
            log.warn("[验证码缓存] setExpire 非法时间 <= 0, key: {}, 忽略续期", key);
            return;
        }
        try {
            Boolean result = stringRedisTemplate.expire(key, l, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(result)) {
                log.debug("[验证码缓存] 续期成功, key: {}, 续期 {}s", key, l);
            } else {
                log.warn("[验证码缓存] 续期失败（key 可能已过期）, key: {}", key);
            }
        } catch (Exception e) {
            log.error("[验证码缓存] Redis expire 异常, key: {}", key, e);
            // 续期失败不抛异常，避免影响用户操作
        }
    }

    @Override
    public Long increment(String key, long val) {
        if (val == 0) {
            return 0L;
        }
        try {
            Long result = stringRedisTemplate.opsForValue().increment(key, val);
            return result != null ? result : val;
        } catch (Exception e) {
            log.error("[验证码缓存] Redis increment 失败, key: {}, val: {}", key, val, e);
            return val; // 降级：返回原值
        }
    }
}
