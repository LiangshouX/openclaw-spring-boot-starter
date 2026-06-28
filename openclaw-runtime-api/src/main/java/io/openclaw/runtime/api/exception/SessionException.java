package io.openclaw.runtime.api.exception;

/** 会话操作失败时抛出的异常。 */
public class SessionException extends OpenClawRuntimeException {

    public SessionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SessionException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public SessionException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public SessionException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
