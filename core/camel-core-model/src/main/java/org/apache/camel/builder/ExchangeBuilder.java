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
package org.apache.camel.builder;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchange;

/**
 * Builder to create {@link Exchange} and add headers and set body on the Exchange {@link Message}.
 * <p/>
 * Use the {@link #build()} method when done setting up the exchange.
 */
public final class ExchangeBuilder {
    private final CamelContext context;
    private ExchangePattern pattern;
    private Object body;
    private final Map<String, Object> headers = new HashMap<>();
    private final Map<String, Object> properties = new HashMap<>();

    public ExchangeBuilder(CamelContext context) {
        this.context = context;
    }

    /**
     * Create the exchange by setting the camel context
     *
     * @param  context the camel context
     * @return         exchange builder
     */
    public static ExchangeBuilder anExchange(CamelContext context) {
        return new ExchangeBuilder(context);
    }

    /**
     * Set the in message body on the exchange
     *
     * @param  body the body
     * @return      exchange builder
     */
    public ExchangeBuilder withBody(Object body) {
        this.body = body;
        return this;
    }

    /**
     * Set the message header of the in message on the exchange
     *
     * @param  key   the key of the header
     * @param  value the value of the header
     * @return       exchange builder
     */
    public ExchangeBuilder withHeader(String key, Object value) {
        headers.put(key, value);
        return this;
    }

    /**
     * Set the message exchange pattern on the exchange
     *
     * @param  pattern exchange pattern
     * @return         exchange builder
     */
    public ExchangeBuilder withPattern(ExchangePattern pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * Set the exchange property
     *
     * @param  key   the key of the exchange property
     * @param  value the value of the exchange property
     * @return       exchange builder
     */
    public ExchangeBuilder withProperty(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Build up the exchange from the exchange builder
     *
     * @return exchange
     */
    public Exchange build() {
        Exchange exchange = new DefaultExchange(context);

        if (pattern != null) {
            exchange.setPattern(pattern);
        }

        exchange.getMessage().setBody(body);

        if (!headers.isEmpty()) {
            exchange.getMessage().setHeaders(headers);
        }
        // setup the properties on the exchange
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            exchange.setProperty(entry.getKey(), entry.getValue());
        }

        return exchange;
    }
}
