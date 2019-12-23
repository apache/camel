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
package org.apache.camel.component.netty.http;

import java.util.Locale;

/**
 * A {@link org.apache.camel.component.netty.http.ContextPathMatcher} that supports the Rest DSL.
 */
public class RestContextPathMatcher extends DefaultContextPathMatcher {

    private final String rawPath;
    private final String comparePath;

    public RestContextPathMatcher(String rawPath, String path, String restrictMethod, boolean matchOnUriPrefix) {
        super(path, matchOnUriPrefix);
        this.rawPath = rawPath;
        this.comparePath = rawPath + "?" + restrictMethod;
    }

    @Override
    public boolean matchesRest(String path, boolean wildcard) {
        return matchRestPath(path, rawPath, wildcard);
    }

    @Override
    public boolean matchMethod(String method, String restrict) {
        if (restrict == null) {
            return true;
        }

        // always match OPTIONS as some REST clients uses that prior to calling the service
        if ("OPTIONS".equals(method)) {
            return true;
        }

        return restrict.toLowerCase(Locale.US).contains(method.toLowerCase(Locale.US));
    }

    /**
     * Matches the given request path with the configured consumer path
     *
     * @param requestPath   the request path
     * @param consumerPath  the consumer path which may use { } tokens
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
     */
    public boolean matchRestPath(String requestPath, String consumerPath, boolean wildcard) {
        // remove starting/ending slashes
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        if (requestPath.endsWith("/")) {
            requestPath = requestPath.substring(0, requestPath.length() - 1);
        }
        // remove starting/ending slashes
        if (consumerPath.startsWith("/")) {
            consumerPath = consumerPath.substring(1);
        }
        if (consumerPath.endsWith("/")) {
            consumerPath = consumerPath.substring(0, consumerPath.length() - 1);
        }

        if (matchOnUriPrefix && (requestPath.startsWith(consumerPath) || consumerPath.isEmpty())) {
            return true;
        }

        // split using single char / is optimized in the jdk
        String[] requestPaths = requestPath.split("/");
        String[] consumerPaths = consumerPath.split("/");

        // must be same number of path's
        if (requestPaths.length != consumerPaths.length) {
            return false;
        }

        for (int i = 0; i < requestPaths.length; i++) {
            String p1 = requestPaths[i];
            String p2 = consumerPaths[i];

            if (wildcard && p2.startsWith("{") && p2.endsWith("}")) {
                // always matches
                continue;
            }

            if (!p1.equals(p2)) {
                return false;
            }
        }

        // assume matching
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RestContextPathMatcher that = (RestContextPathMatcher) o;

        if (comparePath.equals(that.comparePath))  {
            return super.equals(o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * comparePath.hashCode() + (matchOnUriPrefix ? 1 : 0);
    }

}
