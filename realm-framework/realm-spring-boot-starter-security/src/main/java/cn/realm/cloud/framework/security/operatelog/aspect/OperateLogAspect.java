package cn.realm.cloud.framework.security.operatelog.aspect;

import cn.realm.cloud.framework.security.core.SecurityUtils;
import cn.realm.cloud.framework.security.operatelog.annotation.OperateLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * 操作日志切面。
 *
 * <p>依据：ASVS V7 要求所有认证决策记录日志。
 * 本切面将操作人信息输出到日志，满足审计追溯要求。
 *
 * @author QI Guang
 */
@Slf4j
@Aspect
public class OperateLogAspect {

    @Around("@annotation(operateLog)")
    public Object around(ProceedingJoinPoint pjp, OperateLog operateLog) throws Throwable {
        String operator = SecurityUtils.findLoginUser()
                .map(u -> String.valueOf(u.id()))
                .orElse("anonymous");

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("[OperateLog] operator={}, method={}, type={}, desc={}, cost={}ms",
                    operator, sig.toShortString(), operateLog.type(),
                    operateLog.description(), cost);
            return result;
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[OperateLog] operator={}, method={}, type={}, failed, cost={}ms, error={}",
                    operator, sig.toShortString(), operateLog.type(),
                    cost, t.getMessage());
            throw t;
        }
    }
}
