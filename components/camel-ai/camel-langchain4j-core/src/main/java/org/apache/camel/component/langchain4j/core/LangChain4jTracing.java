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
package org.apache.camel.component.langchain4j.core;

import java.util.List;
import java.util.Set;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

/**
 * Tells whether a LangChain4j model already traces its own calls, so that Camel does not record them a second time.
 *
 * @since 4.24
 */
public final class LangChain4jTracing {

    /**
     * Chat model listeners that record each model call as a span or Micrometer Observation.
     */
    private static final Set<String> TRACING_LISTENERS = Set.of(
            // quarkus-langchain4j, when quarkus-opentelemetry is present
            "io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener",
            // dev.langchain4j:langchain4j-observation (Micrometer Observation)
            "dev.langchain4j.observation.listener.ObservationChatModelListener");

    private LangChain4jTracing() {
    }

    /**
     * Whether the given model has a listener that traces its calls.
     */
    public static boolean isTracedByFramework(Object model) {
        if (!(model instanceof ChatModel chatModel)) {
            return false;
        }
        try {
            List<ChatModelListener> listeners = chatModel.listeners();
            return listeners != null
                    && listeners.stream().anyMatch(listener -> TRACING_LISTENERS.contains(listener.getClass().getName()));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
