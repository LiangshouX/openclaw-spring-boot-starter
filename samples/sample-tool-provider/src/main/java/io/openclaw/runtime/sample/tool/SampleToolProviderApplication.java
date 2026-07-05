package io.openclaw.runtime.sample.tool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例应用，演示 OpenClaw Tool 的自动发现和注册。
 */
@SpringBootApplication
public class SampleToolProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleToolProviderApplication.class, args);
    }
}
