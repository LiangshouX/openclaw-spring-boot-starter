package io.openclaw.runtime.api.exception;

/** DTO 转换失败时抛出的异常。 */
public class ConverterException extends OpenClawRuntimeException {

    public ConverterException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConverterException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public ConverterException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ConverterException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
