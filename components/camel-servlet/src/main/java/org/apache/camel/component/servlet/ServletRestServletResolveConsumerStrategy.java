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
package org.apache.camel.component.servlet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpServletResolveConsumerStrategy;

/**
 * A {@link org.apache.camel.component.http.HttpServletResolveConsumerStrategy} that supports the Rest DSL.
 */
public class ServletRestServletResolveConsumerStrategy extends HttpServletResolveConsumerStrategy {

    @Override
    public HttpConsumer resolve(HttpServletRequest request, Map<String, HttpConsumer> consumers) {
        HttpConsumer answer = null;

        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }
        String method = request.getMethod();
        if (method == null) {
            return null;
        }

        List<HttpConsumer> candidates = new ArrayList<HttpConsumer>();

        // first match by http method
        for (Map.Entry<String, HttpConsumer> entry : consumers.entrySet()) {
            String restrict = entry.getValue().getEndpoint().getHttpMethodRestrict();
            if (matchRestMethod(method, restrict)) {
                candidates.add(entry.getValue());
            }
        }

        // then see if we got a direct match
        Iterator<HttpConsumer> it = candidates.iterator();
        while (it.hasNext()) {
            HttpConsumer consumer = it.next();
            String consumerPath = consumer.getPath();
            if (matchRestPath(path, consumerPath, false)) {
                answer = consumer;
                break;
            }
        }

        // then match by wildcard path
        if (answer == null) {
            it = candidates.iterator();
            while (it.hasNext()) {
                HttpConsumer consumer = it.next();
                String consumerPath = consumer.getPath();
                // filter non matching paths
                if (!matchRestPath(path, consumerPath, true)) {
                    it.remove();
                }
            }

            // if there is multiple candidates with wildcards then pick anyone with the least number of wildcards
            int bestWildcard = Integer.MAX_VALUE;
            HttpConsumer best = null;
            if (candidates.size() > 1) {
                it = candidates.iterator();
                while (it.hasNext()) {
                    HttpConsumer entry = it.next();
                    String consumerPath = entry.getPath();
                    int wildcards = countWildcards(consumerPath);
                    if (wildcards > 0) {
                        if (best == null || wildcards < bestWildcard) {
                            best = entry;
                            bestWildcard = wildcards;
                        }
                    }
                }

                if (best != null) {
                    // pick the best among the wildcards
                    answer = best;
                }
            }

            // if there is one left then its our answer
            if (answer == null && candidates.size() == 1) {
                answer = candidates.get(0);
            }
        }

        if (answer == null) {
            // fallback to default
            answer = super.resolve(request, consumers);
        }

        return answer;
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

    /**
     * Counts the number of wildcards in the path
     *
     * @param consumerPath  the consumer path which may use { } tokens
     * @return number of wildcards, or <tt>0</tt> if no wildcards
     */
    public int countWildcards(String consumerPath) {
        int wildcards = 0;

        // remove starting/ending slashes
        if (consumerPath.startsWith("/")) {
            consumerPath = consumerPath.substring(1);
        }
        if (consumerPath.endsWith("/")) {
            consumerPath = consumerPath.substring(0, consumerPath.length() - 1);
        }

        String[] consumerPaths = consumerPath.split("/");
        for (String p2 : consumerPaths) {
            if (p2.startsWith("{") && p2.endsWith("}")) {
                wildcards++;
            }
        }

        return wildcards;
    }

    /**
     * Matches the given request HTTP method with the configured HTTP method of the consumer
     *
     * @param method    the request HTTP method
     * @param restrict  the consumer configured HTTP restrict method
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
     */
    public boolean matchRestMethod(String method, String restrict) {
        if (restrict == null) {
            return true;
        }

        // always match OPTIONS as some REST clients uses that prior to calling the service
        if ("OPTIONS".equals(method)) {
            return true;
        }

        return restrict.toLowerCase(Locale.US).contains(method.toLowerCase(Locale.US));
    }

}
