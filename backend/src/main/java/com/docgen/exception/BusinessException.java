package com.docgen.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 用于在业务逻辑中抛出可预期的异常，携带错误码和错误信息
 * 由 GlobalExceptionHandler 统一捕获并返回给前端
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final int code;

    /**
     * 创建业务异常
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建业务异常（默认错误码 400）
     *
     * @param message 错误信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 创建业务异常（带原始异常）
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原始异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
