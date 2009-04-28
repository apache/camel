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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
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
    private Exception exception;
    private String exchangeId;
    private UnitOfWork unitOfWork;
    private ExchangePattern pattern;
    private Endpoint fromEndpoint;

    public DefaultExchange(CamelContext context) {
        this(context, ExchangePattern.InOnly);
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
    }

    public DefaultExchange(Exchange parent) {
        this(parent.getContext(), parent.getPattern());
        this.unitOfWork = parent.getUnitOfWork();
        this.fromEndpoint = parent.getFromEndpoint();
    }

    public DefaultExchange(Endpoint fromEndpoint) {
        this(fromEndpoint, ExchangePattern.InOnly);
    }
    
    public DefaultExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        this.context = fromEndpoint.getCamelContext();
        this.fromEndpoint = fromEndpoint;
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

    public Exchange newCopy() {
        Exchange exchange = copy();
        // do not share the unit of work
        exchange.setUnitOfWork(null);
        return exchange;
    }

    public void copyFrom(Exchange exchange) {
        if (exchange == this) {
            return;
        }
        setProperties(safeCopy(exchange.getProperties()));

        // this can cause strangeness if we copy, say, a FileMessage onto an FtpExchange with overloaded getExchange() methods etc.
        safeCopy(getIn(), exchange.getIn());
        if (exchange.hasOut()) {
            safeCopy(getOut(), exchange.getOut());
        }
        if (exchange.hasFault()) {
            safeCopy(getFault(), exchange.getFault());
        }
        setException(exchange.getException());

        unitOfWork = exchange.getUnitOfWork();
        pattern = exchange.getPattern();
        setFromEndpoint(exchange.getFromEndpoint());
    }

    private static void safeCopy(Message message, Message that) {
        if (message != null) {
            message.copyFrom(that);
        }
    }

    private static Map<String, Object> safeCopy(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }
        return new ConcurrentHashMap<String, Object>(properties);
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

        return ExchangeHelper.convertToType(this, type, value);
    }

    @SuppressWarnings("unchecked")
    public void setProperty(String name, Object value) {
        ExchangeProperty<?> property = ExchangeProperty.getByName(name);

        // if the property is also a well known property in ExchangeProperty then validate that the
        // value is of the same type
        if (property != null) {
            Class type = value.getClass();
            validateExchangePropertyIsExpectedType(property, type, value);
        }
        if (value != null) {
            // avoid the NullPointException
            getProperties().put(name, value);
        } else {
            // if the value is null , we just remove the key from the map
            if (name != null) {
                getProperties().remove(name);
            }
        }
    }

    private <T> void validateExchangePropertyIsExpectedType(ExchangeProperty<?> property, Class<T> type, Object value) {
        if (value != null && property != null && !property.type().isAssignableFrom(type)) {
            throw new RuntimeCamelException("Type cast exception while getting an "
                    + "Exchange Property value '" + value.toString() + "' on Exchange " + this
                    + " for a well known Exchange Property with these traits: " + property);
        }
    }

    public Object removeProperty(String name) {
        return getProperties().remove(name);
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new ConcurrentHashMap<String, Object>();
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
        if (out == null) {
            out = createOutMessage();
            configureMessage(out);
        }
        return out;
    }

    public boolean hasOut() {
        return out != null;
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

    public Exception getException() {
        return exception;
    }

    public <T> T getException(Class<T> type) {
        if (exception == null) {
            return null;
        }

        Iterator<Throwable> it = ObjectHelper.createExceptionIterator(exception);
        while (it.hasNext()) {
            Throwable e = it.next();
            if (type.isInstance(e)) {
                return type.cast(e);
            }
        }
        // not found
        return null;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public Endpoint getFromEndpoint() {
        return fromEndpoint;
    }

    public void setFromEndpoint(Endpoint fromEndpoint) {
        this.fromEndpoint = fromEndpoint;
    }

    public Message getFault() {
        if (fault == null) {
            fault = createFaultMessage();
            configureMessage(fault);
        }
        return fault;
    }

    public boolean hasFault() {
        return fault != null;
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

    public void removeFault() {
        this.fault = null;
    }

    public String getExchangeId() {
        if (exchangeId == null) {
            exchangeId = createExchangeId();
        }
        return exchangeId;
    }

    public void setExchangeId(String id) {
        this.exchangeId = id;
    }

    public boolean isFailed() {
        if (hasFault()) {
            Object faultBody = getFault().getBody();
            if (faultBody != null) {
                return true;
            }
        }
        return getException() != null;
    }

    public boolean isTransacted() {
        Boolean transacted = getProperty(TRANSACTED, Boolean.class);
        return transacted != null && transacted;
    }

    public boolean isRollbackOnly() {
        Boolean rollback = getProperty(ROLLBACK_ONLY, Boolean.class);
        return rollback != null && rollback;
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

    protected String createExchangeId() {
        String answer = null;
        if (in != null) {
            answer = in.createExchangeId();
        }
        if (answer == null) {
            answer = DefaultExchange.DEFAULT_ID_GENERATOR.generateId();
        }
        return answer;
    }

}
