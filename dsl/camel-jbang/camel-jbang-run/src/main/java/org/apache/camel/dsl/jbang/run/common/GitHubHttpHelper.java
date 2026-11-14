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
package org.apache.camel.dsl.jbang.run.common;

import java.net.http.HttpResponse;

/**
 * Shared HTTP utility methods for GitHub API responses.
 */
final class GitHubHttpHelper {

    private GitHubHttpHelper() {
    }

    /**
     * Returns a hint string for rate-limit or access-denied errors (403/429).
     */
    static String rateLimitHint(HttpResponse<?> res, String token) {
        int status = res.statusCode();
        if (status == 429) {
            // Always a rate-limit response
            return token != null && !token.isBlank()
                    ? " (rate limit exceeded)"
                    : " (rate limit exceeded - set GITHUB_TOKEN to increase the limit)";
        }
        if (status == 403) {
            String remaining = res.headers().firstValue("x-ratelimit-remaining").orElse(null);
            if ("0".equals(remaining)) {
                return token != null && !token.isBlank()
                        ? " (rate limit exceeded)"
                        : " (rate limit exceeded - set GITHUB_TOKEN to increase the limit)";
            }
            return " (access denied - the repository may be private or blocked)";
        }
        return "";
    }
}
