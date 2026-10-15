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
package org.apache.camel.semantic;

import java.util.Map;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.ai.AiEvent;

/**
 * A route-bound Semantic notification. The Exchange is live and is only intended for synchronous listeners; audit
 * storage and asynchronous consumers use {@link #getData()}, which is frozen and contains no Exchange reference.
 */
public final class SemanticExchangeEvent extends SemanticEvent implements AiEvent.ExchangeAiEvent {
    private final Exchange exchange;

    public SemanticExchangeEvent(Kind kind, Map<String, ?> data, Exchange exchange) {
        super(kind, data);
        this.exchange = Objects.requireNonNull(exchange);
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public Object getSource() {
        return exchange;
    }
}
