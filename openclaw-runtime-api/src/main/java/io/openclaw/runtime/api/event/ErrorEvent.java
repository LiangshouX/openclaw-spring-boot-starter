package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 运行时操作过程中发生错误时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ErrorEvent extends RuntimeEvent {

    private String errorCode;
    private String errorMessage;
    private transient Throwable cause;
}
