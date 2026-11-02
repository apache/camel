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

/**
 * How a producer ranks route and MCP tools in one shared search space.
 * <p>
 * {@link #KEYWORD} uses deterministic name and description matching. {@link #VECTOR} uses cosine similarity and
 * requires an {@link AiToolSearchEmbedder}. A missing embedder is rejected; vector search does not fall back to keyword
 * search.
 *
 * @since 4.24
 */
public enum AiToolSearchStrategy {

    /**
     * Case-insensitive contains matching on the tool name and description.
     */
    KEYWORD,

    /**
     * Cosine similarity of embeddings of the tool name and description.
     */
    VECTOR
}
