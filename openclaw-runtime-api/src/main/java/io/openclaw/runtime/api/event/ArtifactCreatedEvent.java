package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 运行时处理过程中创建新制品时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ArtifactCreatedEvent extends RuntimeEvent {

    private String artifactId;
    private String artifactType;
    private String artifactName;
    private String downloadUrl;
}
