package io.openclaw.runtime.tool.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;

/** JSON Schema 生成器，为工具输入参数生成 JSON Schema 表示。 */
public class JsonSchemaGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为给定类的公共字段生成 JSON Schema。
     *
     * @param clazz 要生成 Schema 的类
     * @return 描述类字段的 JSON Schema 节点
     */
    public JsonNode generate(Class<?> clazz) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            ObjectNode fieldSchema = mapType(field.getType());
            properties.set(field.getName(), fieldSchema);
            required.add(field.getName());
        }
        schema.set("properties", properties);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }

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
        ArrayNode required = objectMapper.createArrayNode();

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            ObjectNode paramSchema = mapType(parameter.getType());
            String name = parameter.isNamePresent() ? parameter.getName() : "arg" + i;
            properties.set(name, paramSchema);
            required.add(name);
        }
        schema.set("properties", properties);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }

        return schema;
    }

    private ObjectNode mapType(Class<?> type) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", mapJavaTypeToJsonSchemaType(type));
        return schema;
    }

    private String mapJavaTypeToJsonSchemaType(Class<?> type) {
        if (type == String.class) {
            return "string";
        } else if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == BigInteger.class) {
            return "integer";
        } else if (type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == BigDecimal.class) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (Date.class.isAssignableFrom(type) || Temporal.class.isAssignableFrom(type)) {
            return "string";
        } else if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        } else {
            return "object";
        }
    }
}
