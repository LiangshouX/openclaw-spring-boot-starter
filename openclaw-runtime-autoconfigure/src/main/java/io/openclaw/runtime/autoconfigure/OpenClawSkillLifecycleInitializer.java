package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.skill.loader.DefaultSkillManager;
import io.openclaw.runtime.skill.model.SkillDefinition;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 应用启动时加载和注册 Skill (SKILL.md) 到 OpenClaw Gateway。
 * <p>
 * 此初始化器在 {@link OpenClawSkillAutoConfiguration} 中创建，
 * 仅在 {@code openclaw.auto-register-skill=true} 时激活。
 * <p>
 * 流程：
 * <ol>
 *   <li>从配置目录或默认路径扫描 SKILL.md 文件</li>
 *   <li>加载到 {@link DefaultSkillManager}</li>
 *   <li>调用 {@link SkillRegistrar#registerToOpenClaw} 注册到 Gateway</li>
 * </ol>
 */
public class OpenClawSkillLifecycleInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenClawSkillLifecycleInitializer.class);

    private final DefaultSkillManager skillManager;
    private final SkillRegistrar skillRegistrar;
    private final OpenClawProperties properties;

    public OpenClawSkillLifecycleInitializer(DefaultSkillManager skillManager,
                                              SkillRegistrar skillRegistrar,
                                              OpenClawProperties properties) {
        this.skillManager = skillManager;
        this.skillRegistrar = skillRegistrar;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing OpenClaw Skills...");

        // Load skills from directories
        loadSkills();

        List<SkillDefinition> eligibleSkills = skillManager.getEligibleSkills();
        log.info("Found {} eligible skills out of {} loaded",
                eligibleSkills.size(), skillManager.size());

        if (!eligibleSkills.isEmpty()) {
            try {
                skillRegistrar.registerToOpenClaw(eligibleSkills);
            } catch (Exception e) {
                log.error("Skill registration failed: {}", e.getMessage(), e);
            }
        } else {
            log.info("No eligible skills to register");
        }

        log.info("OpenClaw Skills initialization complete");
    }

    private void loadSkills() {
        OpenClawProperties.SkillProperties skillProps = properties.getSkill();

        // Load from configured directories
        if (skillProps != null && skillProps.getDirectories() != null) {
            for (String dir : skillProps.getDirectories()) {
                Path path = resolvePath(dir);
                if (path.toFile().isDirectory()) {
                    int count = skillManager.loadFromDirectory(path);
                    log.info("Loaded {} skills from configured directory: {} (resolved to: {})", 
                            count, dir, path.toAbsolutePath());
                } else {
                    log.warn("Configured skill directory does not exist: {} (resolved to: {})", 
                            dir, path.toAbsolutePath());
                }
            }
        }

        // Fallback: try common skill directories
        if (skillManager.size() == 0) {
            Path defaultDir = resolvePath("src/main/resources/skills");
            if (defaultDir.toFile().isDirectory()) {
                int count = skillManager.loadFromDirectory(defaultDir);
                log.info("Loaded {} skills from default directory: {}", count, defaultDir);
            } else {
                Path fallbackDir = resolvePath("skills");
                if (fallbackDir.toFile().isDirectory()) {
                    int count = skillManager.loadFromDirectory(fallbackDir);
                    log.info("Loaded {} skills from fallback directory: {}", count, fallbackDir);
                } else {
                    log.info("No skill directories found — skipping skill loading");
                }
            }
        }
    }

    /**
     * 解析路径，支持相对路径和绝对路径。
     * 对于相对路径，首先尝试相对于当前工作目录，
     * 如果不存在，则尝试相对于类路径资源目录。
     *
     * @param pathStr 路径字符串
     * @return 解析后的 Path 对象
     */
    private Path resolvePath(String pathStr) {
        Path path = Paths.get(pathStr);
        
        // 如果是绝对路径，直接返回
        if (path.isAbsolute()) {
            return path.normalize();
        }
        
        // 先尝试作为相对于工作目录的路径
        Path workDirPath = path.toAbsolutePath().normalize();
        if (workDirPath.toFile().exists()) {
            return workDirPath;
        }
        
        // 如果工作目录下不存在，尝试从类路径加载
        // 这对于打包后的 JAR 文件特别重要
        try {
            var resourceUrl = getClass().getClassLoader().getResource(pathStr);
            if (resourceUrl != null) {
                String protocol = resourceUrl.getProtocol();
                if ("file".equals(protocol)) {
                    Path classpathPath = Paths.get(new URI(resourceUrl.toString()));
                    if (classpathPath.toFile().exists()) {
                        log.debug("Resolved path from classpath: {} -> {}", pathStr, classpathPath);
                        return classpathPath;
                    }
                } else if ("jar".equals(protocol)) {
                    // JAR 内资源不能转为文件系统路径，返回原始路径并记录警告
                    log.info("Skill directory '{}' is inside a JAR archive ({}). " +
                            "JAR-internal skills cannot be loaded via file system scanning. " +
                            "Consider extracting the skills directory or using an external path.",
                            pathStr, resourceUrl);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve path from classpath: {}", pathStr, e);
        }
        
        // 返回原始路径（让调用者处理不存在的情况）
        return workDirPath;
    }
}
