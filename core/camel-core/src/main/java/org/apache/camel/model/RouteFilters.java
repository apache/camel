/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import java.util.function.Function;

import org.apache.camel.support.PatternHelper;

/**
 * Used for filtering routes to only include routes matching a function.
 */
public class RouteFilters implements Function<RouteDefinition, Boolean> {

    public final String pattern;

    /**
     * Used for filtering routes to only include routes matching the given pattern, which follows the following rules:
     *
     * - Match by route id
     * - Match by route input endpoint uri
     *
     * The matching is using exact match, by wildcard and regular expression as documented by {@link PatternHelper#matchPattern(String, String)}.
     *
     * For example to only include routes which starts with foo in their route id's, use: foo&#42;
     * And to only include routes which starts from JMS endpoints, use: jms:&#42;
     */
    public static RouteFilters filterByPattern(String pattern) {
        return new RouteFilters(pattern);
    }

    private RouteFilters(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public Boolean apply(RouteDefinition route) {
        boolean match = false;

        String id = route.getId();
        if (id != null) {
            match = PatternHelper.matchPattern(id, pattern);
        }
        if (!match && route.getInput() != null) {
            String uri = route.getInput().getEndpointUri();
            match = PatternHelper.matchPattern(uri, pattern);
        }
        return match;
    }
    
}
