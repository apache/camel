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
package org.apache.camel.component.rest.postman.support;

import org.apache.camel.util.AntPathMatcher;

/**
 * Matches request ids against the {@code requestFilter} option.
 */
public final class PostmanFilters {

    private PostmanFilters() {
    }

    /**
     * Evaluates a comma separated list of Ant style patterns against a qualified slug.
     * <p>
     * A pattern prefixed with {@code !} excludes, and exclusions win over inclusions. When the filter contains only
     * exclusions, everything not excluded is kept, which is what makes {@code !users/deleteUser} usable on its own.
     *
     * @param  qualifiedSlug the folder qualified slug of a request, for example {@code users/getUserById}
     * @param  filter        the filter expression
     * @return               whether the request should be kept
     */
    public static boolean matches(String qualifiedSlug, String filter) {
        boolean hasInclude = false;
        boolean included = false;

        for (String token : filter.split(",")) {
            String pattern = token.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.startsWith("!")) {
                if (AntPathMatcher.INSTANCE.match(pattern.substring(1), qualifiedSlug)) {
                    return false;
                }
            } else {
                hasInclude = true;
                if (AntPathMatcher.INSTANCE.match(pattern, qualifiedSlug)) {
                    included = true;
                }
            }
        }
        return hasInclude ? included : true;
    }
}
