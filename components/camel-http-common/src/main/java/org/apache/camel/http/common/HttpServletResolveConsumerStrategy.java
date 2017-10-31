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
package org.apache.camel.http.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.support.RestConsumerContextPathMatcher;

/**
 * A default implementation of {@link org.apache.camel.http.common.ServletResolveConsumerStrategy}.
 */
public class HttpServletResolveConsumerStrategy implements ServletResolveConsumerStrategy {

    @Override
    public HttpConsumer resolve(HttpServletRequest request, Map<String, HttpConsumer> consumers) {
        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }
        HttpConsumer answer = consumers.get(path);

        if (answer == null) {
            List<HttpConsumer> candidates = new ArrayList<>();
            for (String key : consumers.keySet()) {
                //We need to look up the consumer path here
                String consumerPath = consumers.get(key).getPath();
                HttpConsumer consumer = consumers.get(key);
                boolean matchOnUriPrefix = consumer.getEndpoint().isMatchOnUriPrefix();
                // Just make sure the we get the right consumer path first
                if (RestConsumerContextPathMatcher.matchPath(path, consumerPath, matchOnUriPrefix)) {
                    candidates.add(consumer);
                }
            }

            if (candidates.size() == 1) {
                answer = candidates.get(0);
            } else {
                // extra filter by restrict
                candidates = candidates.stream().filter(c -> matchRestMethod(request.getMethod(), c.getEndpoint().getHttpMethodRestrict())).collect(Collectors.toList());
                if (candidates.size() == 1) {
                    answer = candidates.get(0);
                }
            }
        }

        return answer;
    }

    private static boolean matchRestMethod(String method, String restrict) {
        return restrict == null || restrict.toLowerCase(Locale.ENGLISH).contains(method.toLowerCase(Locale.ENGLISH));
    }


}
