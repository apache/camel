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
package org.apache.camel.model;

import java.util.function.Function;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.support.PatternHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for filtering routes to only include routes matching a function.
 */
@XmlTransient
public final class RouteFilters implements Function<RouteDefinition, Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(RouteFilters.class);

    private final String includesText;
    private final String excludesText;
    private final String[] includes;
    private final String[] excludes;

    private RouteFilters(String include, String exclude) {
        this.includesText = include;
        this.excludesText = exclude;
        this.includes = include != null ? include.split(",") : null;
        this.excludes = exclude != null ? exclude.split(",") : null;
    }

    /**
     * Used for filtering routes routes matching the given pattern, which
     * follows the following rules: - Match by route id - Match by route input
     * endpoint uri The matching is using exact match, by wildcard and regular
     * expression as documented by
     * {@link PatternHelper#matchPattern(String, String)}. For example to only
     * include routes which starts with foo in their route id's, use:
     * include=foo&#42; And to exclude routes which starts from JMS endpoints,
     * use: exclude=jms:&#42; Multiple patterns can be separated by comma, for
     * example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42; Exclude takes precedence over include.
     *
     * @param include the include pattern
     * @param exclude the exclude pattern
     */
    public static Function<RouteDefinition, Boolean> filterByPattern(String include, String exclude) {
        return new RouteFilters(include, exclude);
    }

    @Override
    public Boolean apply(RouteDefinition route) {
        String id = route.getId();
        String uri = route.getInput() != null ? route.getInput().getEndpointUri() : null;

        boolean answer = filter(route, id, uri);
        LOG.debug("Route filter: include={}, exclude={}, id={}, from={} -> {}", includesText, excludesText, id, uri, answer);
        return answer;
    }

    private boolean filter(RouteDefinition route, String id, String uri) {
        boolean match = false;

        // exclude takes precedence
        if (excludes != null) {
            for (String part : excludes) {
                if (PatternHelper.matchPattern(id, part) || PatternHelper.matchPattern(uri, part)) {
                    return false;
                }
            }
        }

        if (includes != null) {
            for (String part : includes) {
                if (PatternHelper.matchPattern(id, part) || PatternHelper.matchPattern(uri, part)) {
                    match = true;
                    break;
                }
            }
        } else {
            // if include has not been set then, we assume its matched as it was
            // not excluded
            match = true;
        }

        return match;
    }

}
