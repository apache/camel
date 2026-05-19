/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.a2a;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.protocol.JsonRpcProtocol;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

final class A2AErrorSupport {

    private static final String ERROR_INFO_TYPE = "type.googleapis.com/google.rpc.ErrorInfo";
    private static final String ERROR_DOMAIN = "a2a-protocol.org";

    private A2AErrorSupport() {
    }

    static void writeRestError(Exchange exchange, int statusCode, String code, String message) throws Exception {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", statusCode);
        error.put("status", httpStatusName(statusCode));
        error.put("message", message);
        error.put("details", List.of(errorInfo(code)));

        byte[] errorJson = A2AJsonMapper.instance().writeValueAsBytes(Map.of("error", error));
        exchange.getMessage().setBody(errorJson);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.CONTENT_TYPE);
    }

    static Map<String, Object> errorInfo(String code) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("@type", ERROR_INFO_TYPE);
        info.put("reason", errorReason(code));
        info.put("domain", ERROR_DOMAIN);
        return info;
    }

    static int httpStatusCode(String code) {
        return switch (code) {
            case "TaskNotFoundError" -> 404;
            case "AuthenticationError" -> 401;
            case "AuthorizationError" -> 403;
            case "ServerBusyError" -> 429;
            case "InvalidAgentResponseError", "InternalError" -> 500;
            case "TaskNotCancelableError", "PushNotificationNotSupportedError", "UnsupportedOperationError",
                    "ContentTypeNotSupportedError", "ExtendedAgentCardNotConfiguredError",
                    "ExtensionSupportRequiredError", "VersionNotSupportedError", "InvalidParamsError" ->
                400;
            default -> 400;
        };
    }

    static int jsonRpcErrorCode(String code) {
        return switch (code) {
            case "TaskNotFoundError" -> -32001;
            case "TaskNotCancelableError" -> -32002;
            case "PushNotificationNotSupportedError" -> -32003;
            case "UnsupportedOperationError" -> -32004;
            case "ContentTypeNotSupportedError" -> -32005;
            case "InvalidAgentResponseError" -> -32006;
            case "ExtendedAgentCardNotConfiguredError" -> -32007;
            case "ExtensionSupportRequiredError" -> -32008;
            case "VersionNotSupportedError" -> -32009;
            case "InvalidParamsError" -> JsonRpcProtocol.INVALID_PARAMS;
            case "InternalError" -> JsonRpcProtocol.INTERNAL_ERROR;
            default -> -32000;
        };
    }

    private static String errorReason(String code) {
        String name = code.endsWith("Error") ? code.substring(0, code.length() - "Error".length()) : code;
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                answer.append('_');
            }
            answer.append(Character.toUpperCase(ch));
        }
        return answer.toString();
    }

    private static String httpStatusName(int statusCode) {
        return switch (statusCode) {
            case 400 -> "INVALID_ARGUMENT";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "PERMISSION_DENIED";
            case 404 -> "NOT_FOUND";
            case 409 -> "FAILED_PRECONDITION";
            case 413 -> "RESOURCE_EXHAUSTED";
            case 429 -> "RESOURCE_EXHAUSTED";
            case 500 -> "INTERNAL";
            default -> "UNKNOWN";
        };
    }
}
