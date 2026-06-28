package io.openclaw.runtime.sample.skill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 示例 Spring Boot 应用，演示 OpenClaw 技能的自动发现和注册。 */
@SpringBootApplication
public class SampleSkillProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleSkillProviderApplication.class, args);
    }
}
