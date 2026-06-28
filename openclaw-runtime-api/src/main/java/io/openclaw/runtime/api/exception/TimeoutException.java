package io.openclaw.runtime.api.exception;

/** 请求或连接超时时抛出的异常。 */
public class TimeoutException extends OpenClawRuntimeException {

    public TimeoutException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TimeoutException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public TimeoutException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public TimeoutException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
