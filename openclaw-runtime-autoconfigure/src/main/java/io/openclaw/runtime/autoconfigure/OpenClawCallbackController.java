package io.openclaw.runtime.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.event.callback.CallbackDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 接收 OpenClaw 回调的 REST 控制器。
 * 端点路径可通过 {@code openclaw.callback.path} 属性配置。
 * <p>
 * 当配置了 {@code openclaw.callback.secret} 时，端点会验证请求头中的 HMAC-SHA256 签名。
 */
@RestController
public class OpenClawCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OpenClawCallbackController.class);

    private final CallbackDispatcher callbackDispatcher;
    private final OpenClawProperties properties;

    public OpenClawCallbackController(CallbackDispatcher callbackDispatcher,
                                       OpenClawProperties properties) {
        this.callbackDispatcher = callbackDispatcher;
        this.properties = properties;
    }

    /**
     * 处理传入的 OpenClaw 回调请求。
     * <p>
     * 当配置了 {@code openclaw.callback.secret} 时，会验证 {@code X-Signature} 请求头中的 HMAC-SHA256 签名。
     * 签名不匹配或缺失时返回 403 Forbidden。
     *
     * @param signature 请求头中的 HMAC 签名（可为 null）
     * @param payload   回调请求的 JSON 请求体
     * @return 签名验证通过返回 HTTP 200，否则返回 HTTP 403
     */
    @PostMapping("${openclaw.callback.path:/openclaw/callback}")
    public ResponseEntity<Void> handleCallback(
            @RequestHeader(value = "${openclaw.callback.signature-header:X-Signature}",
                    required = false) String signature,
            @RequestBody JsonNode payload) {
        String secret = properties.getCallback().getSecret();
        if (secret != null && !secret.isBlank()) {
            if (signature == null || signature.isBlank()) {
                log.warn("Callback rejected: missing HMAC signature");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!verifyHmacSha256(secret, payload.toString(), signature)) {
                log.warn("Callback rejected: invalid HMAC signature");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        log.debug("Received OpenClaw callback (eventType={})", payload.path("eventType").asText("unknown"));
        callbackDispatcher.dispatch(payload);
        return ResponseEntity.ok().build();
    }

    private boolean verifyHmacSha256(String secret, String payload, String expectedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC verification failed due to crypto error", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
