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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Global configuration for GenAI observability in Camel AI components.
 */
@Configurer(extended = true)
public class AiObservabilityConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata(defaultValue = "true")
    private boolean enabled = true;
    @Metadata(defaultValue = "basic", enums = "basic,detailed,full")
    private String level = "basic";
    @Metadata(defaultValue = "8192")
    private int contentMaxLength = 8192;
    @Metadata(defaultValue = "true")
    private boolean deferToFramework = true;

    public AiObservabilityConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables GenAI observability for Camel AI producers (OpenTelemetry spans and Micrometer metrics when backends are
     * present).
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Enables GenAI observability for Camel AI producers (OpenTelemetry spans and Micrometer metrics when backends are
     * present).
     */
    public AiObservabilityConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getLevel() {
        return level;
    }

    /**
     * How much GenAI telemetry is recorded. basic records one span per LLM call with model, token usage and finish
     * reason. detailed adds the structure of agent runs (an agent span with one span per LLM call and per tool call),
     * request parameters, cached tokens and the conversation id. full adds prompts, completions and tool arguments and
     * results, which may hold personal or confidential data. Content and ids never become metric tags.
     */
    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * How much GenAI telemetry is recorded. basic records one span per LLM call with model, token usage and finish
     * reason. detailed adds the structure of agent runs (an agent span with one span per LLM call and per tool call),
     * request parameters, cached tokens and the conversation id. full adds prompts, completions and tool arguments and
     * results, which may hold personal or confidential data. Content and ids never become metric tags.
     */
    public AiObservabilityConfigurationProperties withLevel(String level) {
        this.level = level;
        return this;
    }

    public boolean isDeferToFramework() {
        return deferToFramework;
    }

    /**
     * When the AI framework behind a component already traces its model calls and tool calls (Spring AI with an
     * ObservationRegistry, quarkus-langchain4j, a LangChain4j model with an observation listener), Camel does not
     * record its own span and metrics for them, so that they are counted once.
     */
    public void setDeferToFramework(boolean deferToFramework) {
        this.deferToFramework = deferToFramework;
    }

    /**
     * When the AI framework behind a component already traces its model calls and tool calls (Spring AI with an
     * ObservationRegistry, quarkus-langchain4j, a LangChain4j model with an observation listener), Camel does not
     * record its own span and metrics for them, so that they are counted once.
     */
    public AiObservabilityConfigurationProperties withDeferToFramework(boolean deferToFramework) {
        this.deferToFramework = deferToFramework;
        return this;
    }

    public int getContentMaxLength() {
        return contentMaxLength;
    }

    /**
     * The maximum length of every text (message, tool argument or result) recorded at level full. Longer texts are cut.
     * A value that is not positive means the default.
     */
    public void setContentMaxLength(int contentMaxLength) {
        this.contentMaxLength = contentMaxLength;
    }

    /**
     * The maximum length of every text (message, tool argument or result) recorded at level full. Longer texts are cut.
     * A value that is not positive means the default.
     */
    public AiObservabilityConfigurationProperties withContentMaxLength(int contentMaxLength) {
        this.contentMaxLength = contentMaxLength;
        return this;
    }
}
