package io.openclaw.runtime.skill.registry;

import io.openclaw.runtime.client.http.SessionHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** 技能注册器，处理与 OpenClaw Gateway 之间的技能注册和注销操作。 */
public class SkillRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistrar.class);

    private final SessionHttpClient sessionHttpClient;

    public SkillRegistrar(SessionHttpClient sessionHttpClient) {
        this.sessionHttpClient = sessionHttpClient;
    }

    /**
     * 将给定清单中的所有技能注册到 OpenClaw Gateway。
     *
     * @param manifest 包含待注册技能定义的技能清单
     */
    public void registerToOpenClaw(SkillManifest manifest) {
        log.info("Registering {} skills to OpenClaw", manifest.getSkills().size());
        // Stub: actual registration logic to be implemented
    }

    /**
     * 从 OpenClaw Gateway 注销指定的技能。
     *
     * @param skillNames 要注销的技能名称列表
     */
    public void unregisterFromOpenClaw(List<String> skillNames) {
        log.info("Unregistering {} skills from OpenClaw", skillNames.size());
        // Stub: actual unregistration logic to be implemented
    }
}
