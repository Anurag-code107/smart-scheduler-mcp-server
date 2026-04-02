package com.mcp.smartScheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 response envelope.
 *
 * Exactly one of {@code result} or {@code error} is present (never both).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {

    private String jsonrpc;
    private Object result;
    private RpcError error;
    private Object id;

    // ─── Factory methods ──────────────────────────────────────────────────────

    public static JsonRpcResponse success(Object id, Object result) {
        JsonRpcResponse r = new JsonRpcResponse();
        r.jsonrpc = "2.0";
        r.id = id;
        r.result = result;
        return r;
    }

    public static JsonRpcResponse error(Object id, int code, String message) {
        JsonRpcResponse r = new JsonRpcResponse();
        r.jsonrpc = "2.0";
        r.id = id;
        r.error = new RpcError(code, message);
        return r;
    }

    // ─── Error object ─────────────────────────────────────────────────────────

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RpcError {
        private int code;
        private String message;
    }
}
