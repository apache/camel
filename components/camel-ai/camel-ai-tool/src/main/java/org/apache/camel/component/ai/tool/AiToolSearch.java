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
package org.apache.camel.component.ai.tool;

import java.util.List;

/**
 * Shared names and result text for the synthetic tool-search function.
 * <p>
 * Producers add one function with {@link #TOOL_NAME} to the initial model request. Keyword search reads
 * {@link #TERMS_ARGUMENT}. Vector search reads {@link #QUERY_ARGUMENT}. The function is not a route or MCP tool.
 *
 * @since 4.24
 */
public final class AiToolSearch {

    /**
     * Name of the synthetic search function sent to the model.
     */
    public static final String TOOL_NAME = "tool_search_tool";

    /**
     * Keyword-search argument: a list of individual search terms.
     */
    public static final String TERMS_ARGUMENT = "terms";

    /**
     * Vector-search argument: a natural-language query.
     */
    public static final String QUERY_ARGUMENT = "query";

    /**
     * Default maximum number of tools returned by one search.
     */
    public static final int DEFAULT_MAX_RESULTS = 5;

    private AiToolSearch() {
    }

    /**
     * Text returned to the model after a search. The matched tool definitions are attached separately by the producer.
     */
    public static String resultMessage(List<String> foundToolNames) {
        if (foundToolNames == null || foundToolNames.isEmpty()) {
            return "No matching tools found";
        }
        return "Tools found: " + String.join(", ", foundToolNames);
    }
}
