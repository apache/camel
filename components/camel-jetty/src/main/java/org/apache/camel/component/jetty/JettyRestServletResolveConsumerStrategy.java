/**
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
package org.apache.camel.component.jetty;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpServletResolveConsumerStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link org.apache.camel.component.http.HttpServletResolveConsumerStrategy} that supports the Rest DSL.
 */
public class JettyRestServletResolveConsumerStrategy extends HttpServletResolveConsumerStrategy {

    @Override
    public HttpConsumer resolve(HttpServletRequest request, Map<String, HttpConsumer> consumers) {
        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }

        for (String key : consumers.keySet()) {
            if (useRestMatching(key) && matchRestPath(path, key)) {
                return consumers.get(key);
            }
        }

        // fallback to default
        return super.resolve(request, consumers);
    }

    private boolean useRestMatching(String path) {
        // only need to do rest matching if using { } placeholders
        return path.indexOf('{') > -1;
    }

    /**
     * Matches the given request path with the configured consumer path
     *
     * @param requestPath   the request path
     * @param consumerPath  the consumer path which may use { } tokens
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
     */
    public boolean matchRestPath(String requestPath, String consumerPath) {
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

            if (p2.startsWith("{") && p2.endsWith("}")) {
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

}
