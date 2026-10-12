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
package org.apache.camel.ai;

import org.apache.camel.spi.CamelEvent;
import org.jspecify.annotations.Nullable;

/**
 * Common contract for events fired by Camel AI components (tool calls, agentic loops, ...).
 * <p>
 * An {@code AiEvent} is a {@link CamelEvent.ExchangeEvent} of {@link CamelEvent.Type#Ai}: it carries the exchange it
 * relates to (so it can be correlated by route id or exchange id) and is typed {@code Ai} rather than {@code Custom},
 * so an {@link org.apache.camel.spi.EventNotifier} can recognise AI activity — and route it to metrics, tracing or a
 * GenAI-observability backend — by type and by these sub-interfaces, without depending on any AI component or doing an
 * {@code instanceof} against each component's own event class.
 * <p>
 * Concrete event classes live in the AI components (they implement these interfaces); the only thing shared across
 * components is this contract.
 *
 * @since 4.23
 */
public interface AiEvent extends CamelEvent.ExchangeEvent {

    /**
     * The AI provider the event originates from, e.g. {@code openai} or {@code ai-tool}. Intended as a low-cardinality
     * label for metrics and spans.
     */
    String getProvider();

    @Override
    default Type getType() {
        return Type.Ai;
    }

    /**
     * Fired when a tool call is denied by the route's {@code authorizationPolicy}. Also a
     * {@link CamelEvent.FailureEvent}, so {@link #getCause()} returns the {@code CamelAuthorizationException} the
     * policy raised.
     *
     * @since 4.23
     */
    interface ToolCallDeniedEvent extends AiEvent, CamelEvent.FailureEvent {

        /** The name (route id) of the tool whose call was denied. */
        String getToolName();
    }

    /**
     * Fired after a single tool call has been executed.
     *
     * @since 4.23
     */
    interface ToolCallExecutedEvent extends AiEvent {

        /** The name of the tool that was called. */
        String getToolName();

        /** Whether the tool call completed successfully. */
        boolean isSuccess();

        /** How long the tool call took, in milliseconds. */
        long getDurationMillis();
    }

    /**
     * Fired when an agentic loop starts.
     *
     * @since 4.23
     */
    interface AgentLoopStartedEvent extends AiEvent {

        /** The number of tools available to the loop. */
        int getToolCount();

        /** The maximum number of iterations the loop may run. */
        int getMaxIterations();
    }

    /**
     * Fired when an agentic loop completes.
     *
     * @since 4.23
     */
    interface AgentLoopCompletedEvent extends AiEvent {

        /** The number of iterations the loop actually ran. */
        int getIterationCount();

        /** The total number of tokens the loop consumed, or {@code 0} when not tracked. */
        long getTotalTokens();

        /** Why the loop stopped (for example {@code completed} or {@code max-iterations}), when known. */
        @Nullable
        String getStopReason();
    }
}
