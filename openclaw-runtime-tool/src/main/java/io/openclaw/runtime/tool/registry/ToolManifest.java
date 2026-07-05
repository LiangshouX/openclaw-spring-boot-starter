package io.openclaw.runtime.tool.registry;

import io.openclaw.runtime.api.dto.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** 工具清单 DTO，表示已注册工具的完整清单，用于向 OpenClaw 批量注册。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolManifest {

    /** 清单版本号。 */
    private String version;
    /** 清单生成时间。 */
    private Instant generatedAt;
    /** 清单中包含的工具定义列表。 */
    private List<ToolDefinition> tools;
}
