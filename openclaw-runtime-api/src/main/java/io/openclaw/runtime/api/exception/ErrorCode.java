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
    SKILL_NOT_FOUND("OC-4000", "Skill not found"),
    SKILL_INVOCATION_FAILED("OC-4001", "Skill invocation failed"),
    SKILL_REGISTRATION_FAILED("OC-4002", "Skill registration failed"),
    SKILL_SCHEMA_INVALID("OC-4003", "Invalid skill schema"),
    SKILL_DUPLICATE("OC-4004", "Duplicate skill"),
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
