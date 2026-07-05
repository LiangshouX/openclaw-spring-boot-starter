package io.openclaw.runtime.sample.skill;

import io.openclaw.runtime.skill.loader.DefaultSkillManager;
import io.openclaw.runtime.skill.model.SkillDefinition;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import io.openclaw.runtime.skill.registry.SkillStatusStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST 控制器，提供 Skill 管理 API。
 * <p>
 * 演示如何通过 HTTP 接口查询已加载的 Skill、启用/禁用 Skill，
 * 查看 Skill 的详细内容，以及触发远程注册操作。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final DefaultSkillManager skillManager;

    @Autowired(required = false)
    private SkillRegistrar skillRegistrar;

    @Autowired(required = false)
    private SkillStatusStrategy skillStatusStrategy;

    public SkillController(DefaultSkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 列出所有已加载的 Skill。
     * <p>
     * GET /api/skills
     */
    @GetMapping
    public List<Map<String, Object>> listAllSkills() {
        return skillManager.getAllSkills().stream()
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    /**
     * 列出所有有效（eligible）的 Skill — 已加载且未被禁用。
     * <p>
     * GET /api/skills/eligible
     */
    @GetMapping("/eligible")
    public List<Map<String, Object>> listEligibleSkills() {
        return skillManager.getEligibleSkills().stream()
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定 Skill 的详细信息。
     * <p>
     * GET /api/skills/{name}
     */
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String name) {
        SkillDefinition skill = skillManager.getSkill(name);
        if (skill == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", skill.getName());
        detail.put("description", skill.getDescription());
        detail.put("userInvocable", skill.isUserInvocable());
        detail.put("disableModelInvocation", skill.isDisableModelInvocation());
        detail.put("commandDispatch", skill.getCommandDispatch());
        detail.put("commandTool", skill.getCommandTool());
        detail.put("commandArgMode", skill.getCommandArgMode());
        detail.put("homepage", skill.getHomepage());
        detail.put("metadata", skill.getMetadata());
        detail.put("sourcePath", skill.getSourcePath());
        detail.put("eligible", skillManager.isEligible(name));
        detail.put("bodyLength", skill.getBody() != null ? skill.getBody().length() : 0);
        detail.put("body", skill.getBody());

        return ResponseEntity.ok(detail);
    }

    /**
     * 启用指定 Skill。
     * <p>
     * POST /api/skills/{name}/enable
     */
    @PostMapping("/{name}/enable")
    public ResponseEntity<Map<String, String>> enableSkill(@PathVariable String name) {
        if (skillManager.getSkill(name) == null) {
            return ResponseEntity.notFound().build();
        }
        skillManager.enableSkill(name);
        return ResponseEntity.ok(Map.of("status", "enabled", "name", name));
    }

    /**
     * 禁用指定 Skill。
     * <p>
     * POST /api/skills/{name}/disable
     */
    @PostMapping("/{name}/disable")
    public ResponseEntity<Map<String, String>> disableSkill(@PathVariable String name) {
        if (skillManager.getSkill(name) == null) {
            return ResponseEntity.notFound().build();
        }
        skillManager.disableSkill(name);
        return ResponseEntity.ok(Map.of("status", "disabled", "name", name));
    }

    /**
     * 手动触发所有 Skill 的远程注册。
     * <p>
     * POST /api/skills/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerSkills() {
        if (skillRegistrar == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "SkillRegistrar not available — is auto-register-skill enabled?"));
        }

        List<SkillDefinition> eligible = skillManager.getEligibleSkills();
        try {
            skillRegistrar.registerToOpenClaw(eligible);
            return ResponseEntity.ok(Map.of(
                    "status", "registered",
                    "count", eligible.size()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "failed",
                    "error", e.getMessage()));
        }
    }

    /**
     * 查询 Gateway 上的 Skill 状态。
     * <p>
     * GET /api/skills/gateway-status
     */
    @GetMapping("/gateway-status")
    public ResponseEntity<Map<String, Object>> getGatewayStatus() {
        if (skillStatusStrategy == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "SkillStatusStrategy not available — is auto-register-skill enabled?"));
        }

        try {
            JsonNode status = skillStatusStrategy.queryStatus();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", true);
            result.put("status", status);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "connected", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * 获取管理器状态摘要。
     * <p>
     * GET /api/skills/status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("totalLoaded", skillManager.size());
        status.put("eligible", skillManager.getEligibleSkills().size());
        status.put("disabled", skillManager.getDisabledSkillNames());
        status.put("registrarAvailable", skillRegistrar != null);
        status.put("statusStrategyAvailable", skillStatusStrategy != null);
        return status;
    }

    private Map<String, Object> toSummaryMap(SkillDefinition skill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", skill.getName());
        map.put("description", skill.getDescription());
        map.put("userInvocable", skill.isUserInvocable());
        map.put("eligible", skillManager.isEligible(skill.getName()));
        map.put("hasMetadata", skill.getMetadata() != null && !skill.getMetadata().isEmpty());
        return map;
    }
}
