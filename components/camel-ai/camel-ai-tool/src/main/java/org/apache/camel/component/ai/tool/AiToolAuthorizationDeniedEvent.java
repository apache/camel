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

import java.io.Serial;
import java.util.EventObject;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.ai.AiEvent;

/**
 * Fired when an {@code ai-tool} route's {@code authorizationPolicy} denies a tool call.
 * <p>
 * The policy guards the route's <em>outer</em> processor, so a denial is caught in front of the route: it never enters
 * the route's unit of work and therefore produces no exchange-lifecycle event or route span. This event is the
 * observable signal operators can subscribe to (with an {@code EventNotifier}, JMX, or a micrometer counter) to see and
 * alert on authorization denials, without changing what the model sees — the call is still relayed back to the model as
 * a short refusal.
 * <p>
 * It is an {@link AiEvent.ToolCallDeniedEvent} (of {@link org.apache.camel.spi.CamelEvent.Type#Ai}), so a notifier can
 * recognise it as AI activity without depending on this component; {@link #getExchange()} correlates it with the tool
 * exchange (route id, exchange id), and {@link #getCause()} returns the {@link CamelAuthorizationException} the policy
 * raised.
 *
 * @since 4.23
 */
public final class AiToolAuthorizationDeniedEvent extends EventObject implements AiEvent.ToolCallDeniedEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Exchange exchange;
    private final String toolName;
    private final transient CamelAuthorizationException cause;
    private long timestamp;

    public AiToolAuthorizationDeniedEvent(Exchange exchange, String toolName, CamelAuthorizationException cause) {
        super(exchange);
        this.exchange = exchange;
        this.toolName = toolName;
        this.cause = cause;
    }

    @Override
    public String getProvider() {
        return "ai-tool";
    }

    /**
     * The tool exchange whose call was denied (for correlation, e.g. route id or exchange id).
     */
    @Override
    public Exchange getExchange() {
        return exchange;
    }

    /**
     * The name (route id) of the tool whose call was denied.
     */
    @Override
    public String getToolName() {
        return toolName;
    }

    @Override
    public CamelAuthorizationException getCause() {
        return cause;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AiToolAuthorizationDeniedEvent{toolName='" + toolName + "'}";
    }
}
