package com.qts.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qts.entity.OperationLog;
import com.qts.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志切面：记录业务写操作（排除公开的扫码溯源接口）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final OperationLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Pointcut("execution(* com.qts.controller..*.*(..))")
    public void controllerPoint() {}

    @Pointcut("controllerPoint() && !execution(* com.qts.controller.TraceController..*(..)) " +
            "&& !execution(* com.qts.controller.QrController..*(..))")
    public void bizPoint() {}

    @AfterReturning("bizPoint()")
    public void afterReturn(JoinPoint jp) {
        save(jp, "SUCCESS", null);
    }

    @AfterThrowing(pointcut = "bizPoint()", throwing = "e")
    public void afterThrow(JoinPoint jp, Throwable e) {
        save(jp, "FAILED", e.getMessage());
    }

    private void save(JoinPoint jp, String result, String err) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;
            // 只记录写操作（POST/PUT/DELETE），读接口不进审计日志，避免日志膨胀
            String httpMethod = attrs.getRequest().getMethod();
            if ("GET".equalsIgnoreCase(httpMethod) || "OPTIONS".equalsIgnoreCase(httpMethod)) return;
            OperationLog ol = new OperationLog();
            ol.setMethod(jp.getSignature().toShortString());
            try {
                ol.setParams(objectMapper.writeValueAsString(jp.getArgs()));
            } catch (Exception e) {
                ol.setParams("[unserializable]");
            }
            ol.setResult(err == null ? result : result + ":" + err);
            ol.setLogTime(LocalDateTime.now());
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                ol.setIp(req.getRemoteAddr());
                if (req.getUserPrincipal() != null) {
                    // JWT 场景下 principal 不适用，从 SecurityContext 取
                }
                try {
                    JwtAuthFilter.CurrentUser me = Auths.me();
                    ol.setUserId(me.id());
                    ol.setUsername(me.username());
                } catch (Exception ignored) {}
            }
            ol.setOperation(jp.getSignature().getName());
            logMapper.insert(ol);
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
    }
}
