package com.mcp.smartScheduler.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Incoming JSON-RPC 2.0 request envelope.
 *
 * Example:
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "method":  "createEvent",
 *   "params":  { ... },
 *   "id":      1
 * }
 * </pre>
 */
@Data
public class JsonRpcRequest {

    private String jsonrpc;

    private String method;

    /**
     * Raw JSON params – deserialized into a concrete DTO inside the controller.
     */
    private JsonNode params;

    /**
     * Client-supplied request ID (number or string). Echoed back in the response.
     */
    private Object id;
}
