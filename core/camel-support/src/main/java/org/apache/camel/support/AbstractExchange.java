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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.support.MessageHelper.copyBody;

/**
 * Base class for the two official and only implementations of {@link Exchange}, the {@link DefaultExchange} and
 * {@link DefaultPooledExchange}.
 *
 * Camel end users should use {@link DefaultExchange} if creating an {@link Exchange} manually. However that is more
 * seldom to use, as exchanges are created via {@link Endpoint}.
 *
 * @see DefaultExchange
 */
class AbstractExchange implements ExtendedExchange {

    // number of elements in array
    static final int INTERNAL_LENGTH = ExchangePropertyKey.values().length;
    // empty array for reset
    static final Object[] EMPTY_INTERNAL_PROPERTIES = new Object[INTERNAL_LENGTH];

    final CamelContext context;
    Map<String, Object> properties; // create properties on-demand as we use internal properties mostly
    // optimize for internal exchange properties (not intended for end users)
    final Object[] internalProperties = new Object[INTERNAL_LENGTH];
    long created;
    Message in;
    Message out;
    Exception exception;
    String exchangeId;
    UnitOfWork unitOfWork;
    ExchangePattern pattern;
    Endpoint fromEndpoint;
    String fromRouteId;
    List<Synchronization> onCompletions;
    Boolean externalRedelivered;
    String historyNodeId;
    String historyNodeLabel;
    String historyNodeSource;
    boolean transacted;
    boolean routeStop;
    boolean rollbackOnly;
    boolean rollbackOnlyLast;
    boolean notifyEvent;
    boolean interrupted;
    boolean interruptable = true;
    boolean redeliveryExhausted;
    boolean streamCacheDisabled;
    Boolean errorHandlerHandled;
    AsyncCallback defaultConsumerCallback; // optimize (do not reset)
    Map<String, SafeCopyProperty> safeCopyProperties;

    public AbstractExchange(CamelContext context) {
        this.context = context;
        this.pattern = ExchangePattern.InOnly;
        this.created = System.currentTimeMillis();
    }

    public AbstractExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
        this.created = System.currentTimeMillis();
    }

    public AbstractExchange(Exchange parent) {
        this.context = parent.getContext();
        this.pattern = parent.getPattern();
        this.created = parent.getCreated();
        this.fromEndpoint = parent.getFromEndpoint();
        this.fromRouteId = parent.getFromRouteId();
        this.unitOfWork = parent.getUnitOfWork();
    }

    public AbstractExchange(Endpoint fromEndpoint) {
        this.context = fromEndpoint.getCamelContext();
        this.pattern = fromEndpoint.getExchangePattern();
        this.created = System.currentTimeMillis();
        this.fromEndpoint = fromEndpoint;
    }

    public AbstractExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        this.context = fromEndpoint.getCamelContext();
        this.pattern = pattern;
        this.created = System.currentTimeMillis();
        this.fromEndpoint = fromEndpoint;
    }

    @Override
    public long getCreated() {
        return created;
    }

    @Override
    public Exchange copy() {
        DefaultExchange exchange = new DefaultExchange(this);

        exchange.setIn(getIn().copy());
        copyBody(getIn(), exchange.getIn());
        if (getIn().hasHeaders()) {
            exchange.getIn().setHeaders(safeCopyHeaders(getIn().getHeaders()));
        }
        if (hasOut()) {
            exchange.setOut(getOut().copy());
            copyBody(getOut(), exchange.getOut());
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
        exchange.setStreamCacheDisabled(streamCacheDisabled);

        // copy properties after body as body may trigger lazy init
        if (hasProperties()) {
            copyProperties(getProperties(), exchange.getProperties());
        }

        if (hasSafeCopyProperties()) {
            safeCopyProperties(this.safeCopyProperties, exchange.getSafeCopyProperties());
        }
        // copy over internal properties
        System.arraycopy(internalProperties, 0, exchange.internalProperties, 0, internalProperties.length);

        if (getContext().isMessageHistory()) {
            // safe copy message history using a defensive copy
            List<MessageHistory> history
                    = (List<MessageHistory>) exchange.internalProperties[ExchangePropertyKey.MESSAGE_HISTORY.ordinal()];
            if (history != null) {
                // use thread-safe list as message history may be accessed concurrently
                exchange.internalProperties[ExchangePropertyKey.MESSAGE_HISTORY.ordinal()]
                        = new CopyOnWriteArrayList<>(history);
            }
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
    private void copyProperties(Map<String, Object> source, Map<String, Object> target) {
        target.putAll(source);
    }

    private void safeCopyProperties(
            Map<String, SafeCopyProperty> source, Map<String, SafeCopyProperty> target) {
        source.entrySet().stream().forEach(entry -> {
            target.put(entry.getKey(), entry.getValue().safeCopy());
        });
    }

    @Override
    public CamelContext getContext() {
        return context;
    }

    @Override
    public Object getProperty(ExchangePropertyKey key) {
        return internalProperties[key.ordinal()];
    }

    @Override
    public <T> T getProperty(ExchangePropertyKey key, Class<T> type) {
        Object value = getProperty(key);
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
    public <T> T getProperty(ExchangePropertyKey key, Object defaultValue, Class<T> type) {
        Object value = getProperty(key);
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
    public void setProperty(ExchangePropertyKey key, Object value) {
        internalProperties[key.ordinal()] = value;
    }

    @Override
    public Object removeProperty(ExchangePropertyKey key) {
        Object old = internalProperties[key.ordinal()];
        internalProperties[key.ordinal()] = null;
        return old;
    }

    @Override
    public Object getProperty(String name) {
        Object answer = null;
        ExchangePropertyKey key = ExchangePropertyKey.asExchangePropertyKey(name);
        if (key != null) {
            answer = internalProperties[key.ordinal()];
            // if the property is not an internal then fallback to lookup in the properties map
        }
        if (answer == null && properties != null) {
            answer = properties.get(name);
        }
        return answer;
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
        ExchangePropertyKey key = ExchangePropertyKey.asExchangePropertyKey(name);
        if (key != null) {
            setProperty(key, value);
        } else if (value != null) {
            // avoid the NullPointException
            if (properties == null) {
                this.properties = new ConcurrentHashMap<>(8);
            }
            properties.put(name, value);
        } else if (properties != null) {
            // if the value is null, we just remove the key from the map
            properties.remove(name);
        }
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (this.properties == null) {
            this.properties = new ConcurrentHashMap<>(8);
        } else {
            this.properties.clear();
        }
        this.properties.putAll(properties);
    }

    @Override
    public Object removeProperty(String name) {
        ExchangePropertyKey key = ExchangePropertyKey.asExchangePropertyKey(name);
        if (key != null) {
            return removeProperty(key);
        }
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
        // special optimized
        if (excludePatterns == null && "*".equals(pattern)) {
            if (properties != null) {
                properties.clear();
            }
            // reset array by copying over from empty which is a very fast JVM optimized operation
            System.arraycopy(EMPTY_INTERNAL_PROPERTIES, 0, this.internalProperties, 0, INTERNAL_LENGTH);
            return true;
        }

        boolean matches = false;
        for (ExchangePropertyKey epk : ExchangePropertyKey.values()) {
            String key = epk.getName();
            if (PatternHelper.matchPattern(key, pattern)) {
                if (excludePatterns != null && PatternHelper.isExcludePatternMatch(key, excludePatterns)) {
                    continue;
                }
                matches = true;
                internalProperties[epk.ordinal()] = null;
            }
        }

        // store keys to be removed as we cannot loop and remove at the same time in implementations such as HashMap
        if (properties != null) {
            Set<String> toBeRemoved = null;
            for (String key : properties.keySet()) {
                if (PatternHelper.matchPattern(key, pattern)) {
                    if (excludePatterns != null && PatternHelper.isExcludePatternMatch(key, excludePatterns)) {
                        continue;
                    }
                    matches = true;
                    if (toBeRemoved == null) {
                        toBeRemoved = new HashSet<>();
                    }
                    toBeRemoved.add(key);
                }
            }

            if (matches && toBeRemoved != null) {
                if (toBeRemoved.size() == properties.size()) {
                    // special optimization when all should be removed
                    properties.clear();
                } else {
                    for (String key : toBeRemoved) {
                        properties.remove(key);
                    }
                }
            }
        }

        return matches;
    }

    @Override
    public Map<String, Object> getProperties() {
        if (properties == null) {
            this.properties = new ConcurrentHashMap<>(8);
        }
        return properties;
    }

    Map<String, SafeCopyProperty> getSafeCopyProperties() {
        if (safeCopyProperties == null) {
            this.safeCopyProperties = new ConcurrentHashMap<>(2);
        }
        return safeCopyProperties;
    }

    @Override
    public Map<String, Object> getAllProperties() {
        // include also internal properties (creates a new map)
        Map<String, Object> map = getInternalProperties();
        if (properties != null && !properties.isEmpty()) {
            map.putAll(properties);
        }
        return map;
    }

    @Override
    public boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }

    private boolean hasSafeCopyProperties() {
        return safeCopyProperties != null && !safeCopyProperties.isEmpty();
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
    public <T> T getInOrNull(Class<T> type) {
        if (in == null) {
            return null;
        }
        if (type.isInstance(in)) {
            return type.cast(in);
        }

        return null;
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
                    ? ((MessageSupport) in).newInstance() : new DefaultMessage(getContext());
            configureMessage(out);
        }
        return out;
    }

    @SuppressWarnings("deprecated")
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

    @SuppressWarnings("deprecated")
    @Override
    public boolean hasOut() {
        return out != null;
    }

    @SuppressWarnings("deprecated")
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
        this.transacted = transacted;
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
    public String getHistoryNodeSource() {
        return historyNodeSource;
    }

    @Override
    public void setHistoryNodeSource(String historyNodeSource) {
        this.historyNodeSource = historyNodeSource;
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
        if (interruptable) {
            this.interrupted = interrupted;
        }
    }

    @Override
    public void setInterruptable(boolean interruptable) {
        this.interruptable = interruptable;
    }

    @Override
    public boolean isRedeliveryExhausted() {
        return redeliveryExhausted;
    }

    @Override
    public void setRedeliveryExhausted(boolean redeliveryExhausted) {
        this.redeliveryExhausted = redeliveryExhausted;
    }

    @Override
    public Boolean getErrorHandlerHandled() {
        return errorHandlerHandled;
    }

    @Override
    public boolean isErrorHandlerHandledSet() {
        return errorHandlerHandled != null;
    }

    @Override
    public boolean isErrorHandlerHandled() {
        return errorHandlerHandled;
    }

    @Override
    public void setErrorHandlerHandled(Boolean errorHandlerHandled) {
        this.errorHandlerHandled = errorHandlerHandled;
    }

    @Override
    public boolean isStreamCacheDisabled() {
        return streamCacheDisabled;
    }

    @Override
    public void setStreamCacheDisabled(boolean streamCacheDisabled) {
        this.streamCacheDisabled = streamCacheDisabled;
    }

    /**
     * Configures the message after it has been set on the exchange
     */
    protected void configureMessage(Message message) {
        if (message instanceof MessageSupport) {
            MessageSupport messageSupport = (MessageSupport) message;
            messageSupport.setExchange(this);
            messageSupport.setCamelContext(getContext());
        }
    }

    @Override
    public void copyInternalProperties(Exchange target) {
        AbstractExchange ae = (AbstractExchange) target;
        for (int i = 0; i < internalProperties.length; i++) {
            Object value = internalProperties[i];
            if (value != null) {
                ae.internalProperties[i] = value;
            }
        }
    }

    @Override
    public Map<String, Object> getInternalProperties() {
        Map<String, Object> map = new HashMap<>();
        for (ExchangePropertyKey key : ExchangePropertyKey.values()) {
            Object value = internalProperties[key.ordinal()];
            if (value != null) {
                map.put(key.getName(), value);
            }
        }
        return map;
    }

    @Override
    public AsyncCallback getDefaultConsumerCallback() {
        return defaultConsumerCallback;
    }

    @Override
    public void setDefaultConsumerCallback(AsyncCallback defaultConsumerCallback) {
        this.defaultConsumerCallback = defaultConsumerCallback;
    }

    protected String createExchangeId() {
        return context.getUuidGenerator().generateExchangeUuid();
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
    public void setSafeCopyProperty(String key, SafeCopyProperty value) {
        if (value != null) {
            // avoid the NullPointException
            if (safeCopyProperties == null) {
                this.safeCopyProperties = new ConcurrentHashMap<>(2);
            }
            safeCopyProperties.put(key, value);
        } else if (safeCopyProperties != null) {
            // if the value is null, we just remove the key from the map
            safeCopyProperties.remove(key);
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getSafeCopyProperty(String key, Class<T> type) {
        if (!hasSafeCopyProperties()) {
            return null;
        }
        Object value = getSafeCopyProperties().get(key);

        if (type.isInstance(value)) {
            return (T) value;
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

}
