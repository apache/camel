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
package org.apache.camel.component.langchain4j.ingest;

import java.util.Arrays;

import org.apache.camel.util.AntPathMatcher;

/**
 * The {@code includeId}/{@code excludeId} document id filter the producer applies. Code that sees the document id
 * before the producer, such as a route that parses documents first, can apply the same filter, so a filtered document
 * is never parsed.
 */
public final class IngestIdFilter {

    private final String[] includeIds;
    private final String[] excludeIds;

    private IngestIdFilter(String[] includeIds, String[] excludeIds) {
        this.includeIds = includeIds;
        this.excludeIds = excludeIds;
    }

    /**
     * The filter for the given option values: comma-separated Ant-style patterns, matched case-sensitively; null or
     * blank means no patterns.
     */
    public static IngestIdFilter of(String includeId, String excludeId) {
        return new IngestIdFilter(parsePatterns(includeId), parsePatterns(excludeId));
    }

    /** Exclusion wins over inclusion; with includeId set, only matching ids pass. */
    public boolean accepts(String documentId) {
        if (AntPathMatcher.INSTANCE.anyMatch(excludeIds, documentId)) {
            return false;
        }
        return includeIds == null || AntPathMatcher.INSTANCE.anyMatch(includeIds, documentId);
    }

    // normalised to null when no pattern remains: an all-blank includeId must accept
    // everything, while an empty array handed to anyMatch would reject everything
    private static String[] parsePatterns(String patterns) {
        if (patterns == null || patterns.isBlank()) {
            return null;
        }
        String[] parsed = Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
        return parsed.length == 0 ? null : parsed;
    }
}
