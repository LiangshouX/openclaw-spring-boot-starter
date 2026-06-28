package io.openclaw.runtime.api.exception;

/** 技能操作失败时抛出的异常。 */
public class SkillException extends OpenClawRuntimeException {

    public SkillException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SkillException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public SkillException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public SkillException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
