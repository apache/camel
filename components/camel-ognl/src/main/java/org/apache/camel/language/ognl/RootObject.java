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
package org.apache.camel.language.ognl;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public class RootObject {
    private final Exchange exchange;

    public RootObject(Exchange exchange) {
        this.exchange = exchange;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public CamelContext getContext() {
        return exchange.getContext();
    }

    public Throwable getException() {
        return exchange.getException();
    }

    public String getExchangeId() {
        return exchange.getExchangeId();
    }

    public Message getRequest() {
        return exchange.getIn();
    }   

    public Message getResponse() {
        return exchange.getOut();
    }

    public Map<String, Object> getProperties() {
        return exchange.getProperties();
    }

    public Object getProperty(String name) {
        return exchange.getProperty(name);
    }

    public <T> T getProperty(String name, Class<T> type) {
        return exchange.getProperty(name, type);
    }
}
