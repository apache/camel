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
package org.apache.camel.component.vertx.websocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.vertx.core.http.impl.HttpUtils;
import org.apache.camel.util.ObjectHelper;

public final class VertxWebsocketHelper {

    private VertxWebsocketHelper() {
        // Utility class
    }

    /**
     * Creates a VertxWebsocketHostKey from a given VertxWebsocketConfiguration
     */
    public static VertxWebsocketHostKey createHostKey(URI websockerURI) {
        return new VertxWebsocketHostKey(websockerURI.getHost(), websockerURI.getPort());
    }

    /**
     * Appends a header value to exchange headers, using a List if there are multiple items for the same key
     */
    @SuppressWarnings("unchecked")
    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        if (headers.containsKey(key)) {
            Object existing = headers.get(key);
            List<Object> list;
            if (existing instanceof List) {
                list = (List<Object>) existing;
            } else {
                list = new ArrayList<>();
                list.add(existing);
            }
            list.add(value);
            value = list;
        }
        headers.put(key, value);
    }

    /**
     * Determines whether the path of a WebSocket host (the vertx-websocket consumer) matches a target path (the
     * vertx-websocket producer), taking path parameters and wildcard paths into consideration.
     */
    public static boolean webSocketHostPathMatches(String hostPath, String targetPath) {
        boolean exactPathMatch = true;

        if (ObjectHelper.isEmpty(hostPath) || ObjectHelper.isEmpty(targetPath)) {
            // This scenario should not really be possible as the input args come from the vertx-websocket consumer / producer URI
            return false;
        }

        // Paths ending with '*' are Vert.x wildcard routes so match on the path prefix
        if (hostPath.endsWith("*")) {
            exactPathMatch = false;
            hostPath = hostPath.substring(0, hostPath.lastIndexOf('*'));
        }

        String normalizedHostPath = HttpUtils.normalizePath(hostPath + "/");
        String normalizedTargetPath = HttpUtils.normalizePath(targetPath + "/");
        String[] hostPathElements = normalizedHostPath.split("/");
        String[] targetPathElements = normalizedTargetPath.split("/");

        if (exactPathMatch && hostPathElements.length != targetPathElements.length) {
            return false;
        }

        if (exactPathMatch) {
            return normalizedHostPath.equals(normalizedTargetPath);
        } else {
            return normalizedTargetPath.startsWith(normalizedHostPath);
        }
    }
}
