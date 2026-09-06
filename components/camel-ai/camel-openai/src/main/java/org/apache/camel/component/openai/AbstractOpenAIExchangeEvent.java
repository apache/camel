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

import java.util.EventObject;

import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;

/**
 * Base class for OpenAI agentic loop {@link CamelEvent} notifications.
 * <p>
 * These are {@link CamelEvent.Type#Custom} events and are not {@link CamelEvent.ExchangeEvent} instances so they do not
 * pollute generic exchange lifecycle metrics.
 */
abstract class AbstractOpenAIExchangeEvent extends EventObject implements CamelEvent {

    private final Exchange exchange;
    private long timestamp;

    protected AbstractOpenAIExchangeEvent(Exchange exchange) {
        super(exchange);
        this.exchange = exchange;
    }

    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public Object getSource() {
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

    @Override
    public Type getType() {
        return Type.Custom;
    }
}
