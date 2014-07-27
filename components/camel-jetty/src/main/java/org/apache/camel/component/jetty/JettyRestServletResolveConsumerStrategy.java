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

    // TODO: a better and faster rest pattern matcher, without the .split as they may be slower

    @Override
    public HttpConsumer resolve(HttpServletRequest request, Map<String, HttpConsumer> consumers) {
        HttpConsumer answer = super.resolve(request, consumers);

        if (answer == null) {

            String path = request.getPathInfo();
            if (path == null) {
                return null;
            }

            for (String key : consumers.keySet()) {
                String consumerPath = normalizePath(key);
                String requestPath = normalizePath(path);
                if (matchPaths(requestPath, consumerPath)) {
                    answer = consumers.get(key);
                    break;
                }
            }
        }

        return answer;
    }

    private boolean matchPaths(String requestPath, String consumerPath) {
        String[] requestPaths = requestPath.split("/");
        String[] consumerPaths = consumerPath.split("/");

        // must be same length
        if (requestPaths.length != consumerPaths.length) {
            return false;
        }

        for (int i = 0; i < requestPaths.length; i++) {
            String p1 = requestPaths[i];
            String p2 = consumerPaths[i];

            if (p2.equals("*")) {
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

    protected String normalizePath(String path) {
        StringBuilder sb = new StringBuilder();
        String[] paths = path.split("/");
        for (String s : paths) {
            if (ObjectHelper.isEmpty(s)) {
                continue;
            }
            sb.append("/");
            if (s.startsWith("{") && s.endsWith("}")) {
                sb.append("*");
            } else {
                sb.append(s);
            }
        }
        return sb.toString();
    }

}
