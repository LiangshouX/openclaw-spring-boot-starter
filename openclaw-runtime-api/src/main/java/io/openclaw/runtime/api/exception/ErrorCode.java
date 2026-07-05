package io.openclaw.runtime.api.exception;

import lombok.Getter;

/** OpenClaw Runtime SDK 使用的所有错误码枚举。 */
@Getter
public enum ErrorCode {

    UNKNOWN_ERROR("OC-0000", "Unknown error"),
    CLIENT_ERROR("OC-1000", "Client error"),
    HTTP_ERROR("OC-1001", "HTTP request error"),
    WEBSOCKET_ERROR("OC-1002", "WebSocket error"),
    CONNECTION_REFUSED("OC-1003", "Connection refused"),
    AUTHENTICATION_FAILED("OC-2000", "Authentication failed"),
    TOKEN_EXPIRED("OC-2001", "Token expired"),
    TOKEN_MISSING("OC-2002", "Token missing"),
    SESSION_NOT_FOUND("OC-3000", "Session not found"),
    SESSION_EXPIRED("OC-3001", "Session expired"),
    SESSION_CLOSED("OC-3002", "Session closed"),
    SESSION_CREATE_FAILED("OC-3003", "Failed to create session"),
    TOOL_NOT_FOUND("OC-4000", "Tool not found"),
    TOOL_INVOCATION_FAILED("OC-4001", "Tool invocation failed"),
    TOOL_REGISTRATION_FAILED("OC-4002", "Tool registration failed"),
    TOOL_SCHEMA_INVALID("OC-4003", "Invalid tool schema"),
    TOOL_DUPLICATE("OC-4004", "Duplicate tool"),
    SKILL_UPLOAD_FAILED("OC-4100", "Skill upload failed"),
    SKILL_INSTALL_FAILED("OC-4101", "Skill install failed"),
    CALLBACK_PROCESSING_FAILED("OC-5000", "Callback processing failed"),
    CALLBACK_ENDPOINT_MISSING("OC-5001", "Callback endpoint missing"),
    REGISTER_FAILED("OC-6000", "Registration failed"),
    UNREGISTER_FAILED("OC-6001", "Unregistration failed"),
    REQUEST_TIMEOUT("OC-7000", "Request timeout"),
    CONNECTION_TIMEOUT("OC-7001", "Connection timeout"),
    CONVERSION_FAILED("OC-8000", "Data conversion failed");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
