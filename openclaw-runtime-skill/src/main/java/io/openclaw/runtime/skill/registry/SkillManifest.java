package io.openclaw.runtime.skill.registry;

import io.openclaw.runtime.api.dto.SkillDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** 技能清单 DTO，表示已注册技能的完整清单，用于向 OpenClaw 批量注册。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillManifest {

    private String version;
    private Instant generatedAt;
    private List<SkillDefinition> skills;
}
