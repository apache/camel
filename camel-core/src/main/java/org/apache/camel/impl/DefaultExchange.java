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
import org.apache.camel.Message;
import org.apache.camel.util.UuidGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of {@link Exchange}
 *
 * @version $Revision$
 */
public class DefaultExchange implements Exchange {
    private static final UuidGenerator defaultIdGenerator = new UuidGenerator();
    protected final CamelContext context;
    private Map<String, Object> headers;
    private Message in;
    private Message out;
    private Message fault;
    private Throwable exception;
    private String exchangeId = DefaultExchange.defaultIdGenerator.generateId();

    public DefaultExchange(CamelContext context) {
        this.context = context;
    }

    @Override
    public String toString() {
        return "Exchange[" + in + "]";
    }

    public Exchange copy() {
        Exchange exchange = newInstance();
        exchange.copyFrom(this);
        return exchange;
    }

    public void copyFrom(Exchange exchange) {
        if (exchange == this) {
            return;
        }
        setHeaders(safeCopy(exchange.getProperties()));
        setIn(safeCopy(exchange.getIn()));
        setOut(safeCopy(exchange.getOut()));
       	setFault(safeCopy(exchange.getFault()));        
        setException(exchange.getException());
    }

    static private Map<String, Object> safeCopy(Map<String, Object> properties) {
		if(properties == null)
			return null;
		return new HashMap<String, Object>(properties);
	}

	static private Message safeCopy(Message message) {
    	if( message == null)
    		return null;
    	return message.copy();
	}

	public Exchange newInstance() {
        return new DefaultExchange(context);
    }

    public CamelContext getContext() {
        return context;
    }

    public Object getProperty(String name) {
        if (headers != null) {
            return headers.get(name);
        }
        return null;
    }

    public <T> T getProperty(String name, Class<T> type) {
        Object value = getProperty(name);
        return getContext().getTypeConverter().convertTo(type, value);
    }

    public void setProperty(String name, Object value) {
        getProperties().put(name, value);
    }

    public Map<String, Object> getProperties() {
        if (headers == null) {
            headers = new HashMap<String, Object>();
        }
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public Message getIn() {
        if (in == null) {
            in = createInMessage();
            configureMessage(in);
        }
        return in;
    }

    public void setIn(Message in) {
        this.in = in;
        configureMessage(in);
    }

    public Message getOut() {
        return getOut(true);
    }

    public Message getOut(boolean lazyCreate) {
        if (out == null && lazyCreate) {
            out = createOutMessage();
            configureMessage(out);
        }
        return out;
    }

    public void setOut(Message out) {
        this.out = out;
        configureMessage(out);
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
        configureMessage(fault);
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String id) {
        this.exchangeId = id;
    }

    /**
     * Factory method used to lazily create the IN message
     */
    protected Message createInMessage() {
        return new DefaultMessage();
    }

    /**
     * Factory method to lazily create the OUT message
     */
    protected Message createOutMessage() {
        return new DefaultMessage();
    }

    /**
     * Configures the message after it has been set on the exchange
     */
    protected void configureMessage(Message message) {
        if (message instanceof MessageSupport) {
            MessageSupport messageSupport = (MessageSupport) message;
            messageSupport.setExchange(this);
        }
    }
}
