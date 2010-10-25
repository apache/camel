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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A default implementation of {@link Exchange}
 *
 * @version $Revision$
 */
public final class DefaultExchange implements Exchange {

    protected final CamelContext context;
    private Map<String, Object> properties;
    private Message in;
    private Message out;
    private Exception exception;
    private String exchangeId;
    private UnitOfWork unitOfWork;
    private ExchangePattern pattern;
    private Endpoint fromEndpoint;
    private String fromRouteId;
    private List<Synchronization> onCompletions;

    public DefaultExchange(CamelContext context) {
        this(context, ExchangePattern.InOnly);
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
    }

    public DefaultExchange(Exchange parent) {
        this(parent.getContext(), parent.getPattern());
        this.fromEndpoint = parent.getFromEndpoint();
        this.fromRouteId = parent.getFromRouteId();
        this.unitOfWork = parent.getUnitOfWork();
    }

    public DefaultExchange(Endpoint fromEndpoint) {
        this(fromEndpoint, ExchangePattern.InOnly);
    }
    
    public DefaultExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        this(fromEndpoint.getCamelContext(), pattern);
        this.fromEndpoint = fromEndpoint;
    }

    @Override
    public String toString() {
        return "Exchange[" + (out == null ? in : out) + "]";
    }

    public Exchange copy() {
        DefaultExchange exchange = new DefaultExchange(this);

        exchange.setProperties(safeCopy(getProperties()));
        safeCopy(exchange.getIn(), getIn());
        if (hasOut()) {
            safeCopy(exchange.getOut(), getOut());
        }
        exchange.setException(getException());
        return exchange;
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

    public CamelContext getContext() {
        return context;
    }

    public Object getProperty(String name) {
        if (hasProperties()) {
            return properties.get(name);
        }
        return null;
    }

    public Object getProperty(String name, Object defaultValue) {
        Object answer = null;
        if (hasProperties()) {
            answer = properties.get(name);
        }
        return answer != null ? answer : defaultValue;
    }

    public <T> T getProperty(String name, Class<T> type) {
        Object value = getProperty(name);

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    public <T> T getProperty(String name, Object defaultValue, Class<T> type) {
        Object value = getProperty(name, defaultValue);

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    public void setProperty(String name, Object value) {
        if (value != null) {
            // avoid the NullPointException
            getProperties().put(name, value);
        } else {
            // if the value is null, we just remove the key from the map
            if (name != null) {
                getProperties().remove(name);
            }
        }
    }

    public Object removeProperty(String name) {
        if (!hasProperties()) {
            return null;
        }
        return getProperties().remove(name);
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new ConcurrentHashMap<String, Object>();
        }
        return properties;
    }

    public boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Message getIn() {
        if (in == null) {
            in = new DefaultMessage();
            configureMessage(in);
        }
        return in;
    }

    public <T> T getIn(Class<T> type) {
        Message in = getIn();

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(in)) {
            return type.cast(in);
        }

        // fallback to use type converter
        return context.getTypeConverter().convertTo(type, in);
    }

    public void setIn(Message in) {
        this.in = in;
        configureMessage(in);
    }

    public Message getOut() {
        // lazy create
        if (out == null) {
            out = (in != null && in instanceof MessageSupport)
                ? ((MessageSupport)in).newInstance() : new DefaultMessage();
            configureMessage(out);
        }
        return out;
    }

    public <T> T getOut(Class<T> type) {
        if (!hasOut()) {
            return null;
        }

        Message out = getOut();

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(out)) {
            return type.cast(out);
        }

        // fallback to use type converter
        return context.getTypeConverter().convertTo(type, out);
    }

    public boolean hasOut() {
        return out != null;
    }

    public void setOut(Message out) {
        this.out = out;
        configureMessage(out);
    }

    public Exception getException() {
        return exception;
    }

    public <T> T getException(Class<T> type) {
        return ObjectHelper.getException(type, exception);
    }

    public void setException(Throwable t) {
        if (t == null) {
            this.exception = null;
        } else if (t instanceof Exception) {
            this.exception = (Exception) t;
        } else {
            // wrap throwable into an exception
            this.exception = ObjectHelper.wrapCamelExecutionException(this, t);
        }
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

    public String getFromRouteId() {
        return fromRouteId;
    }

    public void setFromRouteId(String fromRouteId) {
        this.fromRouteId = fromRouteId;
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
        return (hasOut() && getOut().isFault()) || getException() != null;
    }

    public boolean isTransacted() {
        return getUnitOfWork() != null && getUnitOfWork().isTransacted();
    }

    public boolean isRollbackOnly() {
        return Boolean.TRUE.equals(getProperty(Exchange.ROLLBACK_ONLY)) || Boolean.TRUE.equals(getProperty(Exchange.ROLLBACK_ONLY_LAST));
    }

    public UnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public void setUnitOfWork(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
        if (onCompletions != null) {
            // now an unit of work has been assigned so add the on completions
            // we might have registered already
            for (Synchronization onCompletion : onCompletions) {
                unitOfWork.addSynchronization(onCompletion);
            }
            // cleanup the temporary on completion list as they now have been registered
            // on the unit of work
            onCompletions.clear();
            onCompletions = null;
        }
    }

    public void addOnCompletion(Synchronization onCompletion) {
        if (unitOfWork == null) {
            // unit of work not yet registered so we store the on completion temporary
            // until the unit of work is assigned to this exchange by the UnitOfWorkProcessor
            if (onCompletions == null) {
                onCompletions = new ArrayList<Synchronization>();
            }
            onCompletions.add(onCompletion);
        } else {
            getUnitOfWork().addSynchronization(onCompletion);
        }
    }

    public void handoverCompletions(Exchange target) {
        if (onCompletions != null) {
            for (Synchronization onCompletion : onCompletions) {
                target.addOnCompletion(onCompletion);
            }
            // cleanup the temporary on completion list as they have been handed over
            onCompletions.clear();
            onCompletions = null;
        } else if (unitOfWork != null) {
            // let unit of work handover
            unitOfWork.handoverSynchronization(target);
        }
    }

    public List<Synchronization> handoverCompletions() {
        List<Synchronization> answer = null;
        if (onCompletions != null) {
            answer = new ArrayList<Synchronization>(onCompletions);
            onCompletions.clear();
            onCompletions = null;
        }
        return answer;
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
            answer = context.getUuidGenerator().generateUuid();
        }
        return answer;
    }
}
