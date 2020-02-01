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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.ObjectHelper;

/**
 * The default and only implementation of {@link Exchange}.
 */
public final class DefaultExchange implements ExtendedExchange {

    private final CamelContext context;
    private final long created;
    // optimize to create properties always and with a reasonable small size
    private final Map<String, Object> properties = new ConcurrentHashMap<>(8);
    private Message in;
    private Message out;
    private Exception exception;
    private String exchangeId;
    private UnitOfWork unitOfWork;
    private ExchangePattern pattern;
    private Endpoint fromEndpoint;
    private String fromRouteId;
    private List<Synchronization> onCompletions;
    private Boolean externalRedelivered;
    private String historyNodeId;
    private String historyNodeLabel;
    private boolean transacted;
    private boolean routeStop;
    private boolean rollbackOnly;
    private boolean rollbackOnlyLast;
    private boolean notifyEvent;
    private boolean interrupted;
    private boolean redeliveryExhausted;
    private Boolean errorHandlerHandled;

    public DefaultExchange(CamelContext context) {
        this.context = context;
        this.pattern = ExchangePattern.InOnly;
        this.created = System.currentTimeMillis();
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
        this.created = System.currentTimeMillis();
    }

    public DefaultExchange(Exchange parent) {
        this.context = parent.getContext();
        this.pattern = parent.getPattern();
        this.created = parent.getCreated();
        this.fromEndpoint = parent.getFromEndpoint();
        this.fromRouteId = parent.getFromRouteId();
        this.unitOfWork = parent.getUnitOfWork();
    }

    public DefaultExchange(Endpoint fromEndpoint) {
        this.context = fromEndpoint.getCamelContext();
        this.pattern = ExchangePattern.InOnly;
        this.created = System.currentTimeMillis();
        this.fromEndpoint = fromEndpoint;
    }

    public DefaultExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        this.context = fromEndpoint.getCamelContext();
        this.pattern = pattern;
        this.created = System.currentTimeMillis();
        this.fromEndpoint = fromEndpoint;
    }

    @Override
    public String toString() {
        // do not output information about the message as it may contain sensitive information
        if (exchangeId != null) {
            return "Exchange[" + exchangeId + "]";
        } else {
            return "Exchange[]";
        }
    }

    @Override
    public long getCreated() {
        return created;
    }

    @Override
    public Exchange copy() {
        DefaultExchange exchange = new DefaultExchange(this);

        exchange.setIn(getIn().copy());
        exchange.getIn().setBody(getIn().getBody());
        if (getIn().hasHeaders()) {
            exchange.getIn().setHeaders(safeCopyHeaders(getIn().getHeaders()));
        }
        if (hasOut()) {
            exchange.setOut(getOut().copy());
            exchange.getOut().setBody(getOut().getBody());
            if (getOut().hasHeaders()) {
                exchange.getOut().setHeaders(safeCopyHeaders(getOut().getHeaders()));
            }
        }

        exchange.setException(exception);
        exchange.setRouteStop(routeStop);
        exchange.setRollbackOnly(rollbackOnly);
        exchange.setRollbackOnlyLast(rollbackOnlyLast);
        exchange.setNotifyEvent(notifyEvent);
        exchange.setRedeliveryExhausted(redeliveryExhausted);
        exchange.setErrorHandlerHandled(errorHandlerHandled);

        // copy properties after body as body may trigger lazy init
        if (hasProperties()) {
            safeCopyProperties(getProperties(), exchange.getProperties());
        }

        return exchange;
    }

    private Map<String, Object> safeCopyHeaders(Map<String, Object> headers) {
        if (headers == null) {
            return null;
        }

        if (context != null) {
            ExtendedCamelContext ecc = (ExtendedCamelContext) context;
            HeadersMapFactory factory = ecc.getHeadersMapFactory();
            if (factory != null) {
                return factory.newMap(headers);
            }
        }
        // should not really happen but some tests dont start camel context
        return new HashMap<>(headers);
    }

    @SuppressWarnings("unchecked")
    private void safeCopyProperties(Map<String, Object> source, Map<String, Object> target) {
        target.putAll(source);
        if (getContext().isMessageHistory()) {
            // safe copy message history using a defensive copy
            List<MessageHistory> history = (List<MessageHistory>) target.remove(Exchange.MESSAGE_HISTORY);
            if (history != null) {
                target.put(Exchange.MESSAGE_HISTORY, new LinkedList<>(history));
            }
        }
    }

    @Override
    public CamelContext getContext() {
        return context;
    }

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public Object getProperty(String name, Object defaultValue) {
        Object answer = getProperty(name);
        return answer != null ? answer : defaultValue;
    }

    @Override
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

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String name, Object defaultValue, Class<T> type) {
        Object value = getProperty(name);
        if (value == null) {
            value = defaultValue;
        }
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

    @Override
    public void setProperty(String name, Object value) {
        if (value != null) {
            // avoid the NullPointException
            properties.put(name, value);
        } else {
            // if the value is null, we just remove the key from the map
            if (name != null) {
                properties.remove(name);
            }
        }
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        this.properties.clear();
        this.properties.putAll(properties);
    }

    @Override
    public Object removeProperty(String name) {
        if (!hasProperties()) {
            return null;
        }
        return properties.remove(name);
    }

    @Override
    public boolean removeProperties(String pattern) {
        return removeProperties(pattern, (String[]) null);
    }

    @Override
    public boolean removeProperties(String pattern, String... excludePatterns) {
        if (!hasProperties()) {
            return false;
        }

        // store keys to be removed as we cannot loop and remove at the same time in implementations such as HashMap
        Set<String> toBeRemoved = new HashSet<>();
        boolean matches = false;
        for (String key : properties.keySet()) {
            if (PatternHelper.matchPattern(key, pattern)) {
                if (excludePatterns != null && PatternHelper.isExcludePatternMatch(key, excludePatterns)) {
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

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    @Override
    public Message getIn() {
        if (in == null) {
            in = new DefaultMessage(getContext());
            configureMessage(in);
        }
        return in;
    }

    @Override
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

    @Override
    public void setIn(Message in) {
        this.in = in;
        configureMessage(in);
    }

    @Override
    public Message getOut() {
        // lazy create
        if (out == null) {
            out = (in instanceof MessageSupport)
                ? ((MessageSupport)in).newInstance() : new DefaultMessage(getContext());
            configureMessage(out);
        }
        return out;
    }

    @Override
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

    @Override
    public boolean hasOut() {
        return out != null;
    }

    @Override
    public void setOut(Message out) {
        this.out = out;
        configureMessage(out);
    }

    @Override
    public Message getMessage() {
        return hasOut() ? getOut() : getIn();
    }

    @Override
    public <T> T getMessage(Class<T> type) {
        return hasOut() ? getOut(type) : getIn(type);
    }

    @Override
    public void setMessage(Message message) {
        if (hasOut()) {
            setOut(message);
        } else {
            setIn(message);
        }
    }


    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public <T> T getException(Class<T> type) {
        return ObjectHelper.getException(type, exception);
    }

    @Override
    public void setException(Throwable t) {
        if (t == null) {
            this.exception = null;
        } else if (t instanceof Exception) {
            this.exception = (Exception) t;
        } else {
            // wrap throwable into an exception
            this.exception = CamelExecutionException.wrapCamelExecutionException(this, t);
        }
        if (t instanceof InterruptedException) {
            // mark the exchange as interrupted due to the interrupt exception
            setInterrupted(true);
        }
    }

    @Override
    public <T extends Exchange> T adapt(Class<T> type) {
        return type.cast(this);
    }

    @Override
    public ExchangePattern getPattern() {
        return pattern;
    }

    @Override
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public Endpoint getFromEndpoint() {
        return fromEndpoint;
    }

    @Override
    public void setFromEndpoint(Endpoint fromEndpoint) {
        this.fromEndpoint = fromEndpoint;
    }

    @Override
    public String getFromRouteId() {
        return fromRouteId;
    }

    @Override
    public void setFromRouteId(String fromRouteId) {
        this.fromRouteId = fromRouteId;
    }

    @Override
    public String getExchangeId() {
        if (exchangeId == null) {
            exchangeId = createExchangeId();
        }
        return exchangeId;
    }

    @Override
    public void setExchangeId(String id) {
        this.exchangeId = id;
    }

    @Override
    public boolean isFailed() {
        return exception != null;
    }

    @Override
    public boolean isTransacted() {
        return transacted;
    }

    @Override
    public void setTransacted(boolean transacted) {
        this.transacted = true;
    }

    @Override
    public boolean isRouteStop() {
        return routeStop;
    }

    @Override
    public void setRouteStop(boolean routeStop) {
        this.routeStop = routeStop;
    }

    @Override
    public boolean isExternalRedelivered() {
        if (externalRedelivered == null) {
            // lets avoid adding methods to the Message API, so we use the
            // DefaultMessage to allow component specific messages to extend
            // and implement the isExternalRedelivered method.
            Message msg = getIn();
            if (msg instanceof DefaultMessage) {
                externalRedelivered = ((DefaultMessage) msg).isTransactedRedelivered();
            }
            // not from a transactional resource so mark it as false by default
            if (externalRedelivered == null) {
                externalRedelivered = false;
            }
        }
        return externalRedelivered;
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    @Override
    public void setRollbackOnly(boolean rollbackOnly) {
        this.rollbackOnly = rollbackOnly;
    }

    @Override
    public boolean isRollbackOnlyLast() {
        return rollbackOnlyLast;
    }

    @Override
    public void setRollbackOnlyLast(boolean rollbackOnlyLast) {
        this.rollbackOnlyLast = rollbackOnlyLast;
    }

    @Override
    public UnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    @Override
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

    @Override
    public void addOnCompletion(Synchronization onCompletion) {
        if (unitOfWork == null) {
            // unit of work not yet registered so we store the on completion temporary
            // until the unit of work is assigned to this exchange by the unit of work
            if (onCompletions == null) {
                onCompletions = new ArrayList<>();
            }
            onCompletions.add(onCompletion);
        } else {
            getUnitOfWork().addSynchronization(onCompletion);
        }
    }

    @Override
    public boolean containsOnCompletion(Synchronization onCompletion) {
        if (unitOfWork != null) {
            // if there is an unit of work then the completions is moved there
            return unitOfWork.containsSynchronization(onCompletion);
        } else {
            // check temporary completions if no unit of work yet
            return onCompletions != null && onCompletions.contains(onCompletion);
        }
    }

    @Override
    public void handoverCompletions(Exchange target) {
        if (onCompletions != null) {
            for (Synchronization onCompletion : onCompletions) {
                target.adapt(ExtendedExchange.class).addOnCompletion(onCompletion);
            }
            // cleanup the temporary on completion list as they have been handed over
            onCompletions.clear();
            onCompletions = null;
        } else if (unitOfWork != null) {
            // let unit of work handover
            unitOfWork.handoverSynchronization(target);
        }
    }

    @Override
    public List<Synchronization> handoverCompletions() {
        List<Synchronization> answer = null;
        if (onCompletions != null) {
            answer = new ArrayList<>(onCompletions);
            onCompletions.clear();
            onCompletions = null;
        }
        return answer;
    }

    @Override
    public String getHistoryNodeId() {
        return historyNodeId;
    }

    @Override
    public void setHistoryNodeId(String historyNodeId) {
        this.historyNodeId = historyNodeId;
    }

    @Override
    public String getHistoryNodeLabel() {
        return historyNodeLabel;
    }

    @Override
    public void setHistoryNodeLabel(String historyNodeLabel) {
        this.historyNodeLabel = historyNodeLabel;
    }

    @Override
    public boolean isNotifyEvent() {
        return notifyEvent;
    }

    @Override
    public void setNotifyEvent(boolean notifyEvent) {
        this.notifyEvent = notifyEvent;
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    @Override
    public boolean isRedeliveryExhausted() {
        return redeliveryExhausted;
    }

    @Override
    public void setRedeliveryExhausted(boolean redeliveryExhausted) {
        this.redeliveryExhausted = redeliveryExhausted;
    }

    public Boolean getErrorHandlerHandled() {
        return errorHandlerHandled;
    }

    @Override
    public boolean isErrorHandlerHandled() {
        return errorHandlerHandled != null && errorHandlerHandled;
    }

    @Override
    public void setErrorHandlerHandled(Boolean errorHandlerHandled) {
        this.errorHandlerHandled = errorHandlerHandled;
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

    protected String createExchangeId() {
        return context.getUuidGenerator().generateUuid();
    }

}
