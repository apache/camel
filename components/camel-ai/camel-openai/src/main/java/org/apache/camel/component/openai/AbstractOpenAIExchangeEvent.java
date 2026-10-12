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
import java.util.EventObject;

import org.apache.camel.Exchange;
import org.apache.camel.ai.AiEvent;

/**
 * Base class for OpenAI agentic loop {@link AiEvent} notifications.
 * <p>
 * These are {@link org.apache.camel.spi.CamelEvent.Type#Ai} events carrying the exchange they relate to, so an
 * {@code EventNotifier} can recognise them as AI activity and correlate them (route id, exchange id) without depending
 * on this component.
 */
abstract class AbstractOpenAIExchangeEvent extends EventObject implements AiEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Exchange exchange;
    private long timestamp;

    protected AbstractOpenAIExchangeEvent(Exchange exchange) {
        super(exchange);
        this.exchange = exchange;
    }

    @Override
    public String getProvider() {
        return "openai";
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
