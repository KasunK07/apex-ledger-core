package com.ledger.infrastructure.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

import com.ledger.domain.exception.IdempotencyKeyRequiredException;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Pattern KEY_PATTERN = Pattern.compile(IdempotencyKeys.PATTERN);

    @Around("@annotation(idempotent)")
    public Object enforce(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("Idempotent endpoints must be invoked from an HTTP request");
        }
        HttpServletRequest request = attributes.getRequest();
        String key = request.getHeader(IdempotencyKeys.HEADER);
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key must be 8-200 characters and use letters, digits, '.', '_', ':', or '-'");
        }
        if (!(request.getAttribute(IdempotencyRequestAttributes.HASH) instanceof String hash) || hash.isBlank()) {
            throw new IllegalStateException("Idempotency filter did not capture the request hash");
        }
        Object result = joinPoint.proceed();
        if (IdempotencyRequestAttributes.isReplayed()) {
            HttpServletResponse response = attributes.getResponse();
            if (response != null) {
                response.setHeader("Idempotency-Replayed", "true");
            }
        }
        return result;
    }
}
