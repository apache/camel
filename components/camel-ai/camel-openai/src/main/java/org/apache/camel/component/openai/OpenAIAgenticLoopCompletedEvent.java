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
 * Fired when an OpenAI MCP agentic loop completes on an exchange.
 *
 * @since 4.23
 */
public final class OpenAIAgenticLoopCompletedEvent extends AbstractOpenAIExchangeEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int iterationCount;
    private final long totalTokens;
    private final String stopReason;

    public OpenAIAgenticLoopCompletedEvent(
                                           Exchange exchange, int iterationCount, long totalTokens, String stopReason) {
        super(exchange);
        this.iterationCount = iterationCount;
        this.totalTokens = totalTokens;
        this.stopReason = stopReason;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public String getStopReason() {
        return stopReason;
    }
}
