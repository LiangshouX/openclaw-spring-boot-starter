package io.openclaw.runtime.tool.registry;

import io.openclaw.runtime.api.dto.ToolDefinition;
import io.openclaw.runtime.tool.model.ToolMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 工具注册表，内存中管理所有已发现和已注册的 OpenClaw 工具。 */
public class ToolRegistry {

    private final ConcurrentHashMap<String, ToolMetadata> tools = new ConcurrentHashMap<>();

    /**
     * 在注册表中注册单个工具。
     *
     * @param metadata 要注册的工具元数据
     */
    public void register(ToolMetadata metadata) {
        String toolName = metadata.getDefinition().getName();
        metadata.setRegistered(true);
        tools.put(toolName, metadata);
    }

    /**
     * 批量注册所有提供的工具元数据。
     *
     * @param metadataList 要注册的工具元数据列表
     */
    public void registerAll(List<ToolMetadata> metadataList) {
        for (ToolMetadata metadata : metadataList) {
            register(metadata);
        }
    }

    /**
     * 根据名称从注册表中移除工具。
     *
     * @param toolName 要注销的工具名称
     */
    public void unregister(String toolName) {
        ToolMetadata removed = tools.remove(toolName);
        if (removed != null) {
            removed.setRegistered(false);
        }
    }

    /**
     * 根据名称获取已注册工具的元数据。
     *
     * @param toolName 工具名称
     * @return 工具元数据，如果未注册则返回 {@code null}
     */
    public ToolMetadata get(String toolName) {
        return tools.get(toolName);
    }

    /**
     * 返回所有已注册的工具元数据。
     *
     * @return 所有已注册工具元数据的列表
     */
    public List<ToolMetadata> getAll() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 检查指定名称的工具是否已注册。
     *
     * @param toolName 工具名称
     * @return 工具已注册时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isRegistered(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * 构建包含所有已注册工具定义的清单。
     *
     * @return 包含所有已注册工具定义的 {@link ToolManifest}
     */
    public ToolManifest buildManifest() {
        List<ToolDefinition> definitions = tools.values().stream()
                .map(ToolMetadata::getDefinition)
                .collect(Collectors.toList());

        return ToolManifest.builder()
                .version("1.0")
                .generatedAt(Instant.now())
                .tools(definitions)
                .build();
    }
}
