/**
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
package org.apache.camel.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;

public class ExchangeBuilder {
    private CamelContext context;
    private ExchangePattern pattern;
    private Object body;
    private Map<String, Object> headers = new HashMap<String, Object>();
    private Map<String, Object> properties = new HashMap<String, Object>();

    public ExchangeBuilder(CamelContext context) {
        this.context = context;
    }

    public static ExchangeBuilder anExchange(CamelContext context) {
        return new ExchangeBuilder(context);
    }

    public ExchangeBuilder withBody(Object body) {
        this.body = body;
        return this;
    }

    public ExchangeBuilder withHeader(String key, Object value) {
        headers.put(key, value);
        return this;
    }

    public ExchangeBuilder withPattern(ExchangePattern pattern) {
        this.pattern = pattern;
        return this;
    }
    
    public ExchangeBuilder withProperty(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public Exchange build() {
        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getIn();
        message.setBody(body);
        message.setHeaders(headers);
        // setup the properties on the exchange
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            exchange.setProperty(entry.getKey(), entry.getValue());
        }
        if (pattern != null) {
            exchange.setPattern(pattern);
        }

        return exchange;
    }
}
