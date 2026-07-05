package io.openclaw.runtime.api.exception;

/** 工具操作失败时抛出的异常。 */
public class ToolException extends OpenClawRuntimeException {

    public ToolException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ToolException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public ToolException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ToolException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
