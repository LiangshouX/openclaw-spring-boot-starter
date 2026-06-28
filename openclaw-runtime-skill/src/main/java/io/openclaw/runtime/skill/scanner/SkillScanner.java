package io.openclaw.runtime.skill.scanner;

import io.openclaw.runtime.skill.annotation.OpenClawSkill;
import io.openclaw.runtime.skill.model.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 技能扫描器，扫描 Spring ApplicationContext 中标注了 {@code @OpenClawSkill} 的 Bean 并构建元数据。 */
public class SkillScanner {

    private static final Logger log = LoggerFactory.getLogger(SkillScanner.class);

    private final SkillMetadataBuilder metadataBuilder;

    public SkillScanner(SkillMetadataBuilder metadataBuilder) {
        this.metadataBuilder = metadataBuilder;
    }

    /**
     * 扫描给定的应用上下文，查找标注了技能注解的 Bean 并构建其元数据。
     *
     * @param context 要扫描的 Spring 应用上下文
     * @return 已发现的技能元数据列表
     */
    public List<SkillMetadata> scan(ApplicationContext context) {
        List<SkillMetadata> metadataList = new ArrayList<>();
        Map<String, Object> beans = context.getBeansWithAnnotation(OpenClawSkill.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            OpenClawSkill annotation = bean.getClass().getAnnotation(OpenClawSkill.class);
            if (annotation == null) {
                continue;
            }

            SkillMetadata metadata = metadataBuilder.build(bean, annotation);
            metadataList.add(metadata);
            log.info("Discovered skill: name={}, class={}", annotation.name(), bean.getClass().getName());
        }

        return metadataList;
    }
}
