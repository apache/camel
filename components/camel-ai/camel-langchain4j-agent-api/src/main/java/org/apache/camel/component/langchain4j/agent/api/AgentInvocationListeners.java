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
package org.apache.camel.component.langchain4j.agent.api;

import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.observability.api.listener.AiServiceListener;

/**
 * LangChain4j {@link AiServiceListener}s that the agents built from {@link AbstractAgent} register for the invocation
 * running on the current thread.
 * <p/>
 * Camel uses it to observe each model call and tool call of an agent run without changing the user's
 * {@link AgentConfiguration}. Agents build their AI service inside {@code chat}, on the calling thread, so the
 * listeners apply to that invocation only.
 *
 * @since 4.24
 */
public final class AgentInvocationListeners {

    private static final ThreadLocal<List<AiServiceListener<?>>> CURRENT = new ThreadLocal<>();

    private AgentInvocationListeners() {
    }

    /**
     * Runs the agent invocation with the given listeners registered on the AI service it builds.
     */
    public static <T> T invoke(List<AiServiceListener<?>> listeners, Supplier<T> invocation) {
        List<AiServiceListener<?>> previous = CURRENT.get();
        CURRENT.set(List.copyOf(listeners));
        try {
            return invocation.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    /**
     * The listeners of the invocation running on the current thread, empty when none.
     */
    public static List<AiServiceListener<?>> current() {
        List<AiServiceListener<?>> listeners = CURRENT.get();
        return listeners != null ? listeners : List.of();
    }
}
