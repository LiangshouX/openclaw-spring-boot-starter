package io.openclaw.runtime.api.exception;

import lombok.Getter;

/**
 * 所有 OpenClaw SDK 错误的基础运行时异常。
 * 携带 {@link ErrorCode} 和可选的详细信息。
 */
@Getter
public class OpenClawRuntimeException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    /**
     * 使用给定错误码构造异常。
     *
     * @param errorCode 错误码
     */
    public OpenClawRuntimeException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * 使用给定错误码和详细信息构造异常。
     *
     * @param errorCode 错误码
     * @param detail    关于错误的附加详细信息
     */
    public OpenClawRuntimeException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * 使用给定错误码和原因构造异常。
     *
     * @param errorCode 错误码
     * @param cause     底层原因
     */
    public OpenClawRuntimeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * 使用给定错误码、详细信息和原因构造异常。
     *
     * @param errorCode 错误码
     * @param detail    关于错误的附加详细信息
     * @param cause     底层原因
     */
    public OpenClawRuntimeException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
