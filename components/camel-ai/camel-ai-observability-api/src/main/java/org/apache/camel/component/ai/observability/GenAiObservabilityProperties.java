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
package org.apache.camel.component.ai.observability;

/**
 * Global configuration for GenAI observability.
 */
public final class GenAiObservabilityProperties {

    /**
     * Global property to enable or disable GenAI observability across all AI components. Default is {@code true}.
     */
    public static final String ENABLED = "camel.aiObservability.enabled";

    /**
     * Global property selecting how much GenAI telemetry is recorded: {@code basic} (default), {@code detailed} or
     * {@code full}. See {@link GenAiObservabilityLevel}.
     *
     * @since 4.24
     */
    public static final String LEVEL = "camel.aiObservability.level";

    /**
     * Global property capping the length of every text recorded at level {@code full}. Default is
     * {@value #DEFAULT_CONTENT_MAX_LENGTH} characters; a value that is not positive means the default.
     *
     * @since 4.24
     */
    public static final String CONTENT_MAX_LENGTH = "camel.aiObservability.contentMaxLength";

    /**
     * Global property: when the AI framework behind a component already traces its model calls and tool calls (Spring
     * AI with an ObservationRegistry, quarkus-langchain4j, a LangChain4j model with an observation listener), Camel
     * does not record its own span and metrics for them, so they are counted once. Default is {@code true}.
     *
     * @since 4.24
     */
    public static final String DEFER_TO_FRAMEWORK = "camel.aiObservability.deferToFramework";

    /**
     * Default for {@link #CONTENT_MAX_LENGTH}.
     *
     * @since 4.24
     */
    public static final int DEFAULT_CONTENT_MAX_LENGTH = 8192;

    private GenAiObservabilityProperties() {
    }
}
