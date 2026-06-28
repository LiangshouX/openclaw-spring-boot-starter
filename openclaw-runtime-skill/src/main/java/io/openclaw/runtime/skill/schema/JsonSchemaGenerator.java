package io.openclaw.runtime.skill.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** JSON Schema 生成器，为技能输入参数生成 JSON Schema 表示。 */
public class JsonSchemaGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为给定类的字段生成 JSON Schema。
     *
     * @param clazz 要生成 Schema 的类
     * @return 描述类字段的 JSON Schema 节点
     */
    public JsonNode generate(Class<?> clazz) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        for (Field field : clazz.getDeclaredFields()) {
            ObjectNode fieldSchema = objectMapper.createObjectNode();
            fieldSchema.put("type", mapJavaTypeToJsonSchemaType(field.getType()));
            properties.set(field.getName(), fieldSchema);
        }
        schema.set("properties", properties);

        return schema;
    }

    /**
     * 为给定方法的参数生成 JSON Schema。
     *
     * @param method 要生成 Schema 的方法
     * @return 描述方法参数的 JSON Schema 节点
     */
    public JsonNode generate(Method method) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        for (Parameter parameter : method.getParameters()) {
            ObjectNode paramSchema = objectMapper.createObjectNode();
            paramSchema.put("type", mapJavaTypeToJsonSchemaType(parameter.getType()));
            properties.set(parameter.getName(), paramSchema);
        }
        schema.set("properties", properties);

        return schema;
    }

    private String mapJavaTypeToJsonSchemaType(Class<?> type) {
        if (type == String.class) {
            return "string";
        } else if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return "integer";
        } else if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (type.isArray() || java.util.Collection.class.isAssignableFrom(type)) {
            return "array";
        } else {
            return "object";
        }
    }
}
