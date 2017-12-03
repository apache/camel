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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A default implementation of {@link Exchange}
 *
 * @version 
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
        // do not output information about the message as it may contain sensitive information
        return String.format("Exchange[%s]", exchangeId == null ? "" : exchangeId);
    }

    @Override
    public Date getCreated() {
        if (hasProperties()) {
            return getProperty(Exchange.CREATED_TIMESTAMP, Date.class);
        } else {
            return null;
        }
    }

    public Exchange copy() {
        // to be backwards compatible as today
        return copy(false);
    }

    public Exchange copy(boolean safeCopy) {
        DefaultExchange exchange = new DefaultExchange(this);

        if (safeCopy) {
            exchange.getIn().setBody(getIn().getBody());
            exchange.getIn().setFault(getIn().isFault());
            if (getIn().hasHeaders()) {
                exchange.getIn().setHeaders(safeCopyHeaders(getIn().getHeaders()));
                // just copy the attachments here
                exchange.getIn().copyAttachments(getIn());
            }
            if (hasOut()) {
                exchange.getOut().setBody(getOut().getBody());
                exchange.getOut().setFault(getOut().isFault());
                if (getOut().hasHeaders()) {
                    exchange.getOut().setHeaders(safeCopyHeaders(getOut().getHeaders()));
                }
                // Just copy the attachments here
                exchange.getOut().copyAttachments(getOut());
            }
        } else {
            // old way of doing copy which is @deprecated
            // TODO: remove this in Camel 3.0, and always do a safe copy
            exchange.setIn(getIn().copy());
            if (hasOut()) {
                exchange.setOut(getOut().copy());
            }
        }
        exchange.setException(getException());

        // copy properties after body as body may trigger lazy init
        if (hasProperties()) {
            exchange.setProperties(safeCopyProperties(getProperties()));
        }

        return exchange;
    }

    private Map<String, Object> safeCopyHeaders(Map<String, Object> headers) {
        if (headers == null) {
            return null;
        }

        return context.getHeadersMapFactory().newMap(headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeCopyProperties(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }

        Map<String, Object> answer = createProperties(properties);

        // safe copy message history using a defensive copy
        List<MessageHistory> history = (List<MessageHistory>) answer.remove(Exchange.MESSAGE_HISTORY);
        if (history != null) {
            answer.put(Exchange.MESSAGE_HISTORY, new LinkedList<>(history));
        }

        return answer;
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

    public Object getProperty(String name, Object defaultValue) {
        Object answer = getProperty(name);
        return answer != null ? answer : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String name, Class<T> type) {
        Object value = getProperty(name);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return (T) value;
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String name, Object defaultValue, Class<T> type) {
        Object value = getProperty(name, defaultValue);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return (T) value;
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

    public boolean removeProperties(String pattern) {
        return removeProperties(pattern, (String[]) null);
    }

    public boolean removeProperties(String pattern, String... excludePatterns) {
        if (!hasProperties()) {
            return false;
        }

        // store keys to be removed as we cannot loop and remove at the same time in implementations such as HashMap
        Set<String> toBeRemoved = new HashSet<>();
        boolean matches = false;
        for (String key : properties.keySet()) {
            if (EndpointHelper.matchPattern(key, pattern)) {
                if (excludePatterns != null && isExcludePatternMatch(key, excludePatterns)) {
                    continue;
                }
                matches = true;
                toBeRemoved.add(key);
            }
        }

        if (!toBeRemoved.isEmpty()) {
            if (toBeRemoved.size() == properties.size()) {
                // special optimization when all should be removed
                properties.clear();
            } else {
                toBeRemoved.forEach(k -> properties.remove(k));
            }
        }

        return matches;
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = createProperties();
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
            in = new DefaultMessage(getContext());
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
        return context.getTypeConverter().convertTo(type, this, in);
    }

    public void setIn(Message in) {
        this.in = in;
        configureMessage(in);
    }

    public Message getOut() {
        // lazy create
        if (out == null) {
            out = (in instanceof MessageSupport)
                ? ((MessageSupport)in).newInstance() : new DefaultMessage(getContext());
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
        return context.getTypeConverter().convertTo(type, this, out);
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
        if (t instanceof InterruptedException) {
            // mark the exchange as interrupted due to the interrupt exception
            setProperty(Exchange.INTERRUPTED, Boolean.TRUE);
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
        if (exception != null) {
            return true;
        }
        return hasOut() ? getOut().isFault() : getIn().isFault();
    }

    public boolean isTransacted() {
        UnitOfWork uow = getUnitOfWork();
        if (uow != null) {
            return uow.isTransacted();
        } else {
            return false;
        }
    }

    public Boolean isExternalRedelivered() {
        Boolean answer = null;

        // check property first, as the implementation details to know if the message
        // was externally redelivered is message specific, and thus the message implementation
        // could potentially change during routing, and therefore later we may not know if the
        // original message was externally redelivered or not, therefore we store this detail
        // as a exchange property to keep it around for the lifecycle of the exchange
        if (hasProperties()) {
            answer = getProperty(Exchange.EXTERNAL_REDELIVERED, null, Boolean.class);
        }
        
        if (answer == null) {
            // lets avoid adding methods to the Message API, so we use the
            // DefaultMessage to allow component specific messages to extend
            // and implement the isExternalRedelivered method.
            Message msg = getIn();
            if (msg instanceof DefaultMessage) {
                answer = ((DefaultMessage) msg).isTransactedRedelivered();
            }
        }

        return answer;
    }

    public boolean isRollbackOnly() {
        return Boolean.TRUE.equals(getProperty(Exchange.ROLLBACK_ONLY)) || Boolean.TRUE.equals(getProperty(Exchange.ROLLBACK_ONLY_LAST));
    }

    public UnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public void setUnitOfWork(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
        if (unitOfWork != null && onCompletions != null) {
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
            // until the unit of work is assigned to this exchange by the unit of work
            if (onCompletions == null) {
                onCompletions = new ArrayList<Synchronization>();
            }
            onCompletions.add(onCompletion);
        } else {
            getUnitOfWork().addSynchronization(onCompletion);
        }
    }

    public boolean containsOnCompletion(Synchronization onCompletion) {
        if (unitOfWork != null) {
            // if there is an unit of work then the completions is moved there
            return unitOfWork.containsSynchronization(onCompletion);
        } else {
            // check temporary completions if no unit of work yet
            return onCompletions != null && onCompletions.contains(onCompletion);
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
            messageSupport.setCamelContext(getContext());
        }
    }

    @SuppressWarnings("deprecation")
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

    protected Map<String, Object> createProperties() {
        return new HashMap<>();
    }

    protected Map<String, Object> createProperties(Map<String, Object> properties) {
        return new HashMap<>(properties);
    }

    private static boolean isExcludePatternMatch(String key, String... excludePatterns) {
        for (String pattern : excludePatterns) {
            if (EndpointHelper.matchPattern(key, pattern)) {
                return true;
            }
        }
        return false;
    }
}
