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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.UuidGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of {@link Exchange}
 * 
 * @version $Revision$
 */
public class DefaultExchange implements Exchange {
    private static final UuidGenerator DEFAULT_ID_GENERATOR = new UuidGenerator();
    protected final CamelContext context;
    private Map<String, Object> properties;
    private Message in;
    private Message out;
    private Message fault;
    private Throwable exception;
    private String exchangeId = DefaultExchange.DEFAULT_ID_GENERATOR.generateId();
    private UnitOfWork unitOfWork;
    private ExchangePattern pattern;

    public DefaultExchange(CamelContext context) {
        this(context, ExchangePattern.InOnly);
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
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
        setProperties(safeCopy(exchange.getProperties()));

        // this can cause strangeness if we copy, say, a FileMessage onto an FtpExchange with overloaded getExchange() methods etc.
        safeCopy(getIn(), exchange, exchange.getIn());
        Message copyOut = exchange.getOut(false);
        if (copyOut != null) {
            safeCopy(getOut(true), exchange, copyOut);
        }
        Message copyFault = exchange.getFault(false);
        if (copyFault != null) {
            safeCopy(getFault(true), exchange, copyFault);
        }
        setException(exchange.getException());

        unitOfWork = exchange.getUnitOfWork();
        pattern = exchange.getPattern();
    }

    private static void safeCopy(Message message, Exchange exchange, Message that) {
        if (message != null) {
            message.copyFrom(that);
        }
    }

    private static Map<String, Object> safeCopy(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }
        return new HashMap<String, Object>(properties);
    }

    private static Message safeCopy(Exchange exchange, Message message) {
        if (message == null) {
            return null;
        }
        Message answer = message.copy();
        if (answer instanceof MessageSupport) {
            MessageSupport messageSupport = (MessageSupport) answer;
            messageSupport.setExchange(exchange);
        }
        return answer;
    }

    public Exchange newInstance() {
        return new DefaultExchange(context);
    }

    public CamelContext getContext() {
        return context;
    }

    public Object getProperty(String name) {
        if (properties != null) {
            return properties.get(name);
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

    public Object removeProperty(String name) {
        return getProperties().remove(name);
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<String, Object>();
        }
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
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

    public ExchangePattern getPattern() {
        return pattern;
    }

    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public void throwException() throws Exception {
        if (exception == null) {
            return;
        }
        if (exception instanceof Exception) {
            throw (Exception)exception;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException)exception;
        }
        throw new RuntimeCamelException(exception);
    }

    public Message getFault() {
        return getFault(true);
    }

    public Message getFault(boolean lazyCreate) {
        if (fault == null && lazyCreate) {
            fault = createFaultMessage();
            configureMessage(fault);
        }
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
     * Returns true if this exchange failed due to either an exception or fault
     *
     * @see Exchange#getException()
     * @see Exchange#getFault()
     * @return true if this exchange failed due to either an exception or fault
     */
    public boolean isFailed() {
        Message faultMessage = getFault(false);
        if (faultMessage != null) {
            Object faultBody = faultMessage.getBody();
            if (faultBody != null) {
                return true;
            }
        }
        return getException() != null;
    }

    public UnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public void setUnitOfWork(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
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
     * Factory method to lazily create the FAULT message
     */
    protected Message createFaultMessage() {
        return new DefaultMessage();
    }

    /**
     * Configures the message after it has been set on the exchange
     */
    protected void configureMessage(Message message) {
        if (message instanceof MessageSupport) {
            MessageSupport messageSupport = (MessageSupport)message;
            messageSupport.setExchange(this);
        }
    }

}
