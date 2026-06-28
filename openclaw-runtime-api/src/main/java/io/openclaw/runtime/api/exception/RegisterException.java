package io.openclaw.runtime.api.exception;

/** 技能注册或注销失败时抛出的异常。 */
public class RegisterException extends OpenClawRuntimeException {

    public RegisterException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RegisterException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public RegisterException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public RegisterException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
