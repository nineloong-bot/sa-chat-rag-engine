package com.sa.assistant.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    int maxRequests() default 20;

    long windowSeconds() default 60;

    String keyPrefix() default "ratelimit";

    RateLimitKeyStrategy keyStrategy() default RateLimitKeyStrategy.USER_ID;
}
