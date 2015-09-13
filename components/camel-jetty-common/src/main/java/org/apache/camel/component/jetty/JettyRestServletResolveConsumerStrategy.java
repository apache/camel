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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpServletResolveConsumerStrategy;
import org.apache.camel.support.RestConsumerContextPathMatcher;

/**
 * A {@link org.apache.camel.http.common.HttpServletResolveConsumerStrategy} that supports the Rest DSL.
 */
public class JettyRestServletResolveConsumerStrategy extends HttpServletResolveConsumerStrategy {

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

        List<RestConsumerContextPathMatcher.ConsumerPath> paths = new ArrayList<RestConsumerContextPathMatcher.ConsumerPath>();
        for (final Map.Entry<String, HttpConsumer> entry : consumers.entrySet()) {
            paths.add(new RestConsumerContextPathMatcher.ConsumerPath<HttpConsumer>() {
                @Override
                public String getRestrictMethod() {
                    return entry.getValue().getEndpoint().getHttpMethodRestrict();
                }

                @Override
                public String getConsumerPath() {
                    return entry.getValue().getPath();
                }

                @Override
                public HttpConsumer getConsumer() {
                    return entry.getValue();
                }
            });
        }

        RestConsumerContextPathMatcher.ConsumerPath<HttpConsumer> best = RestConsumerContextPathMatcher.matchBestPath(method, path, paths);
        if (best != null) {
            answer = best.getConsumer();
        }

        if (answer == null) {
            // fallback to default
            answer = super.resolve(request, consumers);
        }

        return answer;
    }


}
