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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.UuidGenerator;

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
    private String exchangeId;
    private UnitOfWork unitOfWork;
    private ExchangePattern pattern;

    public DefaultExchange(CamelContext context) {
        this(context, ExchangePattern.InOnly);
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
    }

    public DefaultExchange(DefaultExchange parent) {
        this(parent.getContext(), parent.getPattern());
        this.unitOfWork = parent.getUnitOfWork();
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
        return new DefaultExchange(this);
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

        // if the property is also a well known property in ExchangeProperty then validate that the
        // value is of the same type
        ExchangeProperty<?> property = ExchangeProperty.getByName(name);
        if (property != null) {
            validateExchangePropertyIsExpectedType(property, type, value);
        }

        return getContext().getTypeConverter().convertTo(type, value);
    }

    public void setProperty(String name, Object value) {
        ExchangeProperty<?> property = ExchangeProperty.getByName(name);

        // if the property is also a well known property in ExchangeProperty then validate that the
        // value is of the same type
        if (property != null) {
            Class type = value.getClass();
            validateExchangePropertyIsExpectedType(property, type, value);
        }

        getProperties().put(name, value);
    }

    private <T> void validateExchangePropertyIsExpectedType(ExchangeProperty<?> property, Class<T> type, Object value) {
        if (value != null && property != null && !property.type().isAssignableFrom(type)) {
            throw new RuntimeCamelException("Type cast exception while getting an "
                    + "Exchange Property value '" + value.toString()
                    + "' on Exchange " + this
                    + " for a well known Exchange Property with these traits: " + property);
        }
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
        if (exception instanceof RuntimeException) {
            throw (RuntimeException)exception;
        }
        if (exception instanceof Exception) {
            throw (Exception)exception;
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
        if (exchangeId == null) {
            exchangeId = DefaultExchange.DEFAULT_ID_GENERATOR.generateId();
        }
        return exchangeId;
    }

    public void setExchangeId(String id) {
        this.exchangeId = id;
    }

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

    public boolean isTransacted() {
        ExchangeProperty<?> property = ExchangeProperty.get("transacted");
        return property != null && property.get(this) == Boolean.TRUE;
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
