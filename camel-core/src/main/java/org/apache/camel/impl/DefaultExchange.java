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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Headers;
import org.apache.camel.Message;

/**
 * A default implementation of {@link Exchange}
 *
 * @version $Revision$
 */
public class DefaultExchange implements Exchange {
    protected final CamelContext context;
    private Headers headers;
    private Message in;
    private Message out;
    private Message fault;
    private Throwable exception;
    private String exchangeId;

    public DefaultExchange(CamelContext context) {
        this.context = context;
    }

    public CamelContext getContext() {
        return context;
    }

    public Headers getHeaders() {
        if (headers == null) {
            headers = new DefaultHeaders();
        }
        return headers;
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    public Message getIn() {
        if (in == null) {
            in = createInMessage();
        }
        return in;
    }

    public void setIn(Message in) {
        this.in = in;
    }

    public Message getOut() {
        if (out == null) {
            out = createOutMessage();
        }
        return out;
    }

    public void setOut(Message out) {
        this.out = out;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public Message getFault() {
        return fault;
    }

    public void setFault(Message fault) {
        this.fault = fault;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String id) {
        this.exchangeId = id;
    }

    protected Message createInMessage() {
        return new DefaultMessage();
    }

    protected Message createOutMessage() {
        return new DefaultMessage();
    }

}
