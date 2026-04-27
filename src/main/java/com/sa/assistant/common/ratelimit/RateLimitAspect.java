package com.sa.assistant.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final SlidingWindowRateLimiter rateLimiter;

    @Around("@annotation(com.sa.assistant.common.ratelimit.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = buildKey(rateLimit);
        long windowMs = rateLimit.windowSeconds() * 1000;

        rateLimiter.checkOrThrow(key, windowMs, rateLimit.maxRequests());

        return joinPoint.proceed();
    }

    private String buildKey(RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder(rateLimit.keyPrefix());
        keyBuilder.append(":");

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String userId = "anonymous";
        String ip = "unknown";

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ip = resolveIp(request);
            userId = resolveUserId(request);
        }

        switch (rateLimit.keyStrategy()) {
            case USER_ID -> keyBuilder.append("uid:").append(userId);
            case IP -> keyBuilder.append("ip:").append(ip);
            case USER_ID_AND_IP -> keyBuilder.append("uid:").append(userId).append(":ip:").append(ip);
        }

        return keyBuilder.toString();
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String resolveUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            return resolveIp(request);
        }
        return userId;
    }
}
