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
package org.apache.camel.component.openai;

import java.io.Serial;

import org.apache.camel.Exchange;

/**
 * Fired when an OpenAI MCP agentic loop starts on an exchange.
 *
 * @since 4.23
 */
public final class OpenAIAgenticLoopStartedEvent extends AbstractOpenAIExchangeEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int toolCount;
    private final int maxIterations;

    public OpenAIAgenticLoopStartedEvent(Exchange exchange, int toolCount, int maxIterations) {
        super(exchange);
        this.toolCount = toolCount;
        this.maxIterations = maxIterations;
    }

    public int getToolCount() {
        return toolCount;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    @Override
    public String toString() {
        return "OpenAIAgenticLoopStartedEvent{toolCount=" + toolCount + ", maxIterations=" + maxIterations + "}";
    }
}
