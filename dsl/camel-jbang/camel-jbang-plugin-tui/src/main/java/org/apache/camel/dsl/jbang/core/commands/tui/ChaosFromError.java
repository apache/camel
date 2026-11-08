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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A real error as a chaos rule (CAMEL-26723): {@code !} on the Errors tab makes the same failure again, so a fix can be
 * proved against it without waiting for it to happen again. The rule fails the step the error happened at (else its
 * endpoint, else its route) with the same exception and message ten times; an HTTP failure fails the sends to the same
 * endpoint in the same route with the same status.
 */
final class ChaosFromError {

    // how often the rule fails: enough to see the error handling at work, and it stops by itself
    static final int TIMES = 10;

    private static final int MAX_MESSAGE = 120;
    private static final Pattern HTTP_STATUS = Pattern.compile("statusCode: (\\d{3})");
    private static final Pattern HTTP_URI = Pattern.compile("invoking (\\S+) with statusCode");

    private ChaosFromError() {
    }

    /**
     * The rule that makes the error again, or null when the error says too little (no step, endpoint or route, or no
     * exception).
     */
    static String rule(ErrorInfo error) {
        if (error == null || error.exceptionType == null || error.exceptionType.isBlank()) {
            return null;
        }
        Integer status = httpStatus(error);
        String httpTarget = status != null ? httpTarget(error) : null;
        String target = httpTarget != null ? httpTarget : target(error);
        if (target == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(target).append(" error");
        if (httpTarget != null) {
            // what the HTTP endpoint answered: chaos raises the failure of the component for it, at its sends
            sb.append(" status=").append(status);
        } else {
            sb.append(" exception=").append(error.exceptionType.strip());
            String message = message(error.exceptionMessage);
            if (message != null) {
                sb.append(" message=\"").append(message).append('"');
            }
        }
        return sb.append(" until=").append(TIMES).toString();
    }

    private static String target(ErrorInfo error) {
        if (error.nodeId != null && !error.nodeId.isBlank()) {
            return "step=" + error.nodeId;
        }
        if (error.endpointUri != null && !error.endpointUri.isBlank()) {
            int query = error.endpointUri.indexOf('?');
            return "endpoint=" + (query > 0 ? error.endpointUri.substring(0, query) : error.endpointUri);
        }
        if (error.routeId != null && !error.routeId.isBlank()) {
            return "route=" + error.routeId;
        }
        return null;
    }

    /** The HTTP endpoint that answered with the status, in the route of the error: status= fires at its sends. */
    private static String httpTarget(ErrorInfo error) {
        String uri = error.endpointUri;
        if (uri == null || !uri.startsWith("http")) {
            Matcher m = HTTP_URI.matcher(error.exceptionMessage);
            uri = m.find() ? m.group(1) : null;
        }
        if (uri == null) {
            return null;
        }
        int query = uri.indexOf('?');
        String target = "endpoint=" + (query > 0 ? uri.substring(0, query) : uri);
        return error.routeId != null && !error.routeId.isBlank() ? target + " in=" + error.routeId : target;
    }

    private static Integer httpStatus(ErrorInfo error) {
        if (!error.exceptionType.endsWith("HttpOperationFailedException") || error.exceptionMessage == null) {
            return null;
        }
        Matcher m = HTTP_STATUS.matcher(error.exceptionMessage);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** The message on one line, without double quotes (the rule quotes it), and not too long. */
    private static String message(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String line = text.strip().replaceAll("\\s+", " ").replace('"', '\'').replace("\\", "/");
        return line.length() > MAX_MESSAGE ? line.substring(0, MAX_MESSAGE - 3) + "..." : line;
    }
}
