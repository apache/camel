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
package org.apache.camel.http.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.support.RestConsumerContextPathMatcher;

/**
 * A {@link org.apache.camel.http.common.HttpServletResolveConsumerStrategy} that supports the Rest DSL.
 */
public class HttpRestServletResolveConsumerStrategy extends HttpServletResolveConsumerStrategy {

    @Override
    protected HttpConsumer doResolve(HttpServletRequest request, String method, Map<String, HttpConsumer> consumers) {
        HttpConsumer answer = null;

        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }
        List<RestConsumerContextPathMatcher.ConsumerPath> paths = new ArrayList<>();
        for (final Map.Entry<String, HttpConsumer> entry : consumers.entrySet()) {
            paths.add(new HttpRestConsumerPath(entry.getValue()));
        }

        RestConsumerContextPathMatcher.ConsumerPath<HttpConsumer> best = RestConsumerContextPathMatcher.matchBestPath(method, path, paths);
        if (best != null) {
            answer = best.getConsumer();
        }

        if (answer == null) {
            // fallback to default
            answer = super.doResolve(request, method, consumers);
        }

        return answer;
    }
}
