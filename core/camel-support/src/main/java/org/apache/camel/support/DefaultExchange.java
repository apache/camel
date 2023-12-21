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
package org.apache.camel.support;

import java.util.EnumMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.clock.Clock;

/**
 * The default and only implementation of {@link Exchange}.
 */
public final class DefaultExchange extends AbstractExchange {
    private final Clock timeInfo;

    DefaultExchange(CamelContext context, EnumMap<ExchangePropertyKey, Object> internalProperties,
                    Map<String, Object> properties) {
        super(context, internalProperties, properties);
        this.timeInfo = new MonotonicClock();
    }

    public DefaultExchange(CamelContext context) {
        super(context);
        this.timeInfo = new MonotonicClock();
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
        this.timeInfo = new MonotonicClock();
    }

    public DefaultExchange(Exchange parent) {
        super(parent);
        this.timeInfo = parent.getClock();
    }

    DefaultExchange(AbstractExchange parent) {
        super(parent);
        this.timeInfo = parent.getClock();
    }

    @Override
    public Clock getClock() {
        return timeInfo;
    }

    @Override
    AbstractExchange newCopy() {
        return new DefaultExchange(this);
    }

    public static DefaultExchange newFromEndpoint(Endpoint fromEndpoint) {
        return newFromEndpoint(fromEndpoint, fromEndpoint.getExchangePattern());
    }

    public static DefaultExchange newFromEndpoint(Endpoint fromEndpoint, ExchangePattern exchangePattern) {
        DefaultExchange exchange = new DefaultExchange(fromEndpoint.getCamelContext(), exchangePattern);
        exchange.getExchangeExtension().setFromEndpoint(fromEndpoint);
        return exchange;
    }
}
