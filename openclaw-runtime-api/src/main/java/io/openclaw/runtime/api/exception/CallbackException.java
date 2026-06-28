package io.openclaw.runtime.api.exception;

/** 回调处理失败时抛出的异常。 */
public class CallbackException extends OpenClawRuntimeException {

    public CallbackException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CallbackException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public CallbackException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public CallbackException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
