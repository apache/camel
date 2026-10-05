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
import java.util.Map;

import org.apache.camel.util.CollectionHelper;
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
        CollectionHelper.appendEntry(headers, key, value);
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

        String normalizedHostPath = normalizePath(hostPath + "/");
        String normalizedTargetPath = normalizePath(targetPath + "/");
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

    /**
     * Normalizes a path in the same way that Vert.x normalizes the path of an HTTP request. A leading slash is
     * guaranteed, percent-encoded unreserved characters are decoded, dot segments are removed as described in RFC 3986
     * section 5.2.4 and consecutive slashes are collapsed into one.
     */
    private static String normalizePath(String path) {
        if (path.isEmpty()) {
            return "/";
        }

        if (path.indexOf('%') == -1 && path.indexOf('.') == -1 && !path.contains("//")) {
            // Nothing to decode or remove
            return path.charAt(0) == '/' ? path : "/" + path;
        }

        StringBuilder buffer = new StringBuilder(path.length() + 1);
        if (path.charAt(0) != '/') {
            buffer.append('/');
        }
        buffer.append(path);
        decodeUnreservedCharacters(buffer);
        return removeDotSegments(buffer);
    }

    /**
     * Decodes the percent-encoded characters that RFC 3986 section 2.3 defines as unreserved. Any other percent-encoded
     * character is left as it is.
     */
    private static void decodeUnreservedCharacters(StringBuilder path) {
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) != '%') {
                continue;
            }

            if (i + 3 > path.length()) {
                throw new IllegalArgumentException("Invalid position for escape character: " + i);
            }

            String escapeSequence = path.substring(i + 1, i + 3);
            int unescaped;
            try {
                unescaped = Integer.parseInt(escapeSequence, 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid escape sequence: %" + escapeSequence);
            }
            if (unescaped < 0) {
                throw new IllegalArgumentException("Invalid escape sequence: %" + escapeSequence);
            }

            if (isUnreservedCharacter(unescaped)) {
                path.setCharAt(i, (char) unescaped);
                path.delete(i + 1, i + 3);
            }
        }
    }

    private static boolean isUnreservedCharacter(int c) {
        return c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c >= '0' && c <= '9'
                || c == '-' || c == '.' || c == '_' || c == '~';
    }

    /**
     * Removes the dot segments of a path that starts with a slash, and collapses consecutive slashes into one.
     */
    private static String removeDotSegments(StringBuilder path) {
        StringBuilder result = new StringBuilder(path.length());

        // Each iteration handles one segment, where start is the position of the slash that precedes it
        int start = 0;
        while (start != -1) {
            int end = path.indexOf("/", start + 1);
            String segment = path.substring(start + 1, end == -1 ? path.length() : end);
            boolean dotSegment = segment.equals(".") || segment.equals("..");

            if (segment.equals("..")) {
                // Step back over the previous segment
                result.setLength(Math.max(result.lastIndexOf("/"), 0));
            }

            // A dot segment is dropped, but when it ends the path its slash is preserved
            if (!dotSegment || end == -1) {
                if (result.length() == 0 || result.charAt(result.length() - 1) != '/') {
                    result.append('/');
                }
                if (!dotSegment) {
                    result.append(segment);
                }
            }

            start = end;
        }

        return result.toString();
    }
}
