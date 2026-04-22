package com.docgen.exception;

import com.docgen.dto.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一捕获和处理各类异常，返回标准格式的错误响应
 * 响应格式：{code: int, message: String, data: Object}
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（BusinessException）
     * 返回业务层定义的错误码和错误信息
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status;
        int code = e.getCode();
        if (code == 401) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (code == 403) {
            status = HttpStatus.FORBIDDEN;
        } else if (code == 404) {
            status = HttpStatus.NOT_FOUND;
        } else if (code >= 500) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常（MethodArgumentNotValidException）
     * 当 @Valid 校验失败时触发，返回具体的字段错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败: {}", e.getMessage());

        // 收集所有字段的校验错误
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        // 返回带字段错误数据的错误响应
        Result<Map<String, String>> result = Result.error(400, "参数校验失败");
        result.setData(fieldErrors);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理约束违反异常（ConstraintViolationException）
     * 当方法参数上的 @Validated 校验失败时触发
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("约束校验失败: {}", e.getMessage());

        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        return ResponseEntity
                .badRequest()
                .body(Result.error(400, message));
    }

    /**
     * 处理文件上传大小超限异常（MaxUploadSizeExceededException）
     * 当上传文件超过配置的最大大小时触发
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超限: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "文件大小超过限制，最大允许 50MB"));
    }

    /**
     * 处理访问拒绝异常（AccessDeniedException）
     * 当用户没有权限访问某个资源时触发
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("访问被拒绝: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, "没有权限访问该资源"));
    }

    /**
     * 处理非法参数异常（IllegalArgumentException）
     * 当传入非法参数时触发
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(Result.error(400, e.getMessage()));
    }

    /**
     * 处理所有未捕获的异常（兜底处理）
     * 记录错误日志，返回通用的服务器错误信息
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器内部错误，请稍后重试"));
    }
}
