package io.openclaw.runtime.api.exception;

/** 与 OpenClaw 通信时发生 HTTP 客户端错误时抛出的异常。 */
public class ClientException extends OpenClawRuntimeException {

    public ClientException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ClientException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public ClientException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ClientException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
