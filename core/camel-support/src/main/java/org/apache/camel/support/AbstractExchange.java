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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.trait.message.MessageTrait;
import org.apache.camel.trait.message.RedeliveryTraitPayload;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for the two official and only implementations of {@link Exchange}, the {@link DefaultExchange} and
 * {@link DefaultPooledExchange}.
 *
 * Camel end users should use {@link DefaultExchange} if creating an {@link Exchange} manually. However that is more
 * seldom to use, as exchanges are created via {@link Endpoint}.
 *
 * @see DefaultExchange
 */
abstract class AbstractExchange implements Exchange {
    protected final EnumMap<ExchangePropertyKey, Object> internalProperties;

    protected final CamelContext context;
    protected Map<String, Object> properties; // create properties on-demand as we use internal properties mostly
    protected Message in;
    protected Message out;
    protected Exception exception;
    protected String exchangeId;
    protected ExchangePattern pattern;
    protected boolean routeStop;
    protected boolean rollbackOnly;
    protected boolean rollbackOnlyLast;
    protected Map<String, SafeCopyProperty> safeCopyProperties;
    protected ExchangeVariableRepository variableRepository;
    private final ExtendedExchangeExtension privateExtension;
    private RedeliveryTraitPayload externalRedelivered = RedeliveryTraitPayload.UNDEFINED_REDELIVERY;

    protected AbstractExchange(CamelContext context, EnumMap<ExchangePropertyKey, Object> internalProperties,
                               Map<String, Object> properties) {
        this.context = context;
        this.internalProperties = new EnumMap<>(internalProperties);
        this.privateExtension = new ExtendedExchangeExtension(this);
        this.properties = safeCopyProperties(properties);
    }

    protected AbstractExchange(CamelContext context) {
        this(context, ExchangePattern.InOnly);
    }

    protected AbstractExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;

        internalProperties = new EnumMap<>(ExchangePropertyKey.class);
        privateExtension = new ExtendedExchangeExtension(this);
    }

    protected AbstractExchange(Exchange parent) {
        this.context = parent.getContext();
        this.pattern = parent.getPattern();

        internalProperties = new EnumMap<>(ExchangePropertyKey.class);

        privateExtension = new ExtendedExchangeExtension(this);
        privateExtension.setFromEndpoint(parent.getFromEndpoint());
        privateExtension.setFromRouteId(parent.getFromRouteId());
        privateExtension.setUnitOfWork(parent.getUnitOfWork());
    }

    protected AbstractExchange(AbstractExchange parent) {
        this.context = parent.getContext();
        this.pattern = parent.getPattern();

        this.internalProperties = new EnumMap<>(parent.internalProperties);

        privateExtension = new ExtendedExchangeExtension(this);
        privateExtension.setFromEndpoint(parent.getFromEndpoint());
        privateExtension.setFromRouteId(parent.getFromRouteId());
        privateExtension.setUnitOfWork(parent.getUnitOfWork());

        setIn(parent.getIn().copy());

        if (parent.hasOut()) {
            setOut(parent.getOut().copy());
        }

        setException(parent.exception);
        setRouteStop(parent.routeStop);
        setRollbackOnly(parent.rollbackOnly);
        setRollbackOnlyLast(parent.rollbackOnlyLast);

        privateExtension.setNotifyEvent(parent.getExchangeExtension().isNotifyEvent());
        privateExtension.setRedeliveryExhausted(parent.getExchangeExtension().isRedeliveryExhausted());
        privateExtension.setErrorHandlerHandled(parent.getExchangeExtension().getErrorHandlerHandled());
        privateExtension.setStreamCacheDisabled(parent.getExchangeExtension().isStreamCacheDisabled());

        if (parent.hasVariables()) {
            if (this.variableRepository == null) {
                this.variableRepository = new ExchangeVariableRepository(getContext());
            }
            this.variableRepository.copyFrom(parent.variableRepository);
        }
        if (parent.hasProperties()) {
            this.properties = safeCopyProperties(parent.properties);
        }
        if (parent.hasSafeCopyProperties()) {
            this.safeCopyProperties = parent.copySafeCopyProperties();
        }
    }

    @Override
    public long getCreated() {
        return getClock().getCreated();
    }

    abstract AbstractExchange newCopy();

    @Override
    public Exchange copy() {
        AbstractExchange exchange = newCopy();

        if (getContext().isMessageHistory()) {
            exchange.internalProperties.computeIfPresent(ExchangePropertyKey.MESSAGE_HISTORY,
                    (k, v) -> new CopyOnWriteArrayList<>((List<MessageHistory>) v));
        }

        return exchange;
    }

    @Override
    public CamelContext getContext() {
        return context;
    }

    @Override
    public Object getProperty(ExchangePropertyKey key) {
        return internalProperties.get(key);
    }

    @Override
    public <T> T getProperty(ExchangePropertyKey key, Class<T> type) {
        Object value = getProperty(key);
        return evalPropertyValue(type, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T evalPropertyValue(final Class<T> type, final Object value) {
        if (value == null) {
            // let's avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already is the same type
        if (type.isInstance(value)) {
            return (T) value;
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    // TODO: fix re-assignment of the value instance here.
    @SuppressWarnings("unchecked")
    private <T> T evalPropertyValue(final Object defaultValue, final Class<T> type, Object value) {
        if (value == null) {
            value = defaultValue;
        }
        if (value == null) {
            // let's avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already is the same type
        if (type.isInstance(value)) {
            return (T) value;
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    @Override
    public <T> T getProperty(ExchangePropertyKey key, Object defaultValue, Class<T> type) {
        Object value = getProperty(key);
        return evalPropertyValue(defaultValue, type, value);
    }

    @Override
    public void setProperty(ExchangePropertyKey key, Object value) {
        internalProperties.put(key, value);
    }

    @Override
    public Object removeProperty(ExchangePropertyKey key) {
        return internalProperties.remove(key);
    }

    @Override
    public Object getProperty(String name) {
        Object answer = null;
        ExchangePropertyKey key = ExchangePropertyKey.asExchangePropertyKey(name);
        if (key != null) {
            answer = internalProperties.get(key);
            // if the property is not an internal then fallback to lookup in the properties map
        }
        if (answer == null && properties != null) {
            answer = properties.get(name);
        }
        return answer;
    }

    @Override
    public <T> T getProperty(String name, Class<T> type) {
        Object value = getProperty(name);
        return evalPropertyValue(type, value);
    }

    @Override
    public <T> T getProperty(String name, Object defaultValue, Class<T> type) {
        Object value = getProperty(name);
        return evalPropertyValue(defaultValue, type, value);
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

    void setProperties(Map<String, Object> properties) {
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
            internalProperties.clear();
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
                internalProperties.remove(epk);
            }
        }

        // store keys to be removed as we cannot loop and remove at the same time in implementations such as HashMap
        if (properties != null) {
            Set<String> toBeRemoved = PatternHelper.matchingSet(properties, pattern, excludePatterns);

            if (toBeRemoved != null) {
                matches = true;
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

    private Map<String, SafeCopyProperty> copySafeCopyProperties() {
        Map<String, SafeCopyProperty> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, SafeCopyProperty> entry : this.safeCopyProperties.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().safeCopy());
        }

        return copy;
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
    public Object getVariable(String name) {
        if (variableRepository != null) {
            return variableRepository.getVariable(name);
        }
        return null;
    }

    @Override
    public <T> T getVariable(String name, Class<T> type) {
        Object value = getVariable(name);
        return evalPropertyValue(type, value);
    }

    @Override
    public <T> T getVariable(String name, Object defaultValue, Class<T> type) {
        Object value = getVariable(name);
        return evalPropertyValue(defaultValue, type, value);
    }

    @Override
    public void setVariable(String name, Object value) {
        if (variableRepository == null) {
            variableRepository = new ExchangeVariableRepository(getContext());
        }
        variableRepository.setVariable(name, value);
    }

    @Override
    public Object removeVariable(String name) {
        if (variableRepository != null) {
            if ("*".equals(name)) {
                variableRepository.clear();
                return null;
            }
            return variableRepository.removeVariable(name);
        }
        return null;
    }

    @Override
    public Map<String, Object> getVariables() {
        if (variableRepository == null) {
            // force creating variables
            variableRepository = new ExchangeVariableRepository(getContext());
        }
        return variableRepository.getVariables();
    }

    @Override
    public boolean hasVariables() {
        if (variableRepository != null) {
            return variableRepository.hasVariables();
        }
        return false;
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

    <T> T getInOrNull(Class<T> type) {
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
            privateExtension.setInterrupted(true);
        }
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
        return privateExtension.getFromEndpoint();
    }

    @Override
    public String getFromRouteId() {
        return privateExtension.getFromRouteId();
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
        return privateExtension.isTransacted();
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
        if (externalRedelivered == RedeliveryTraitPayload.UNDEFINED_REDELIVERY) {
            Message message = getIn();

            externalRedelivered = (RedeliveryTraitPayload) message.getPayloadForTrait(MessageTrait.REDELIVERY);
        }

        return externalRedelivered == RedeliveryTraitPayload.IS_REDELIVERY;
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
        return privateExtension.getUnitOfWork();
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

    void copyInternalProperties(Exchange target) {
        ((AbstractExchange) target).internalProperties.putAll(internalProperties);
    }

    Map<String, Object> getInternalProperties() {
        Map<String, Object> map = new HashMap<>();
        for (ExchangePropertyKey key : ExchangePropertyKey.values()) {
            Object value = internalProperties.get(key);
            if (value != null) {
                map.put(key.getName(), value);
            }
        }
        return map;
    }

    protected String createExchangeId() {
        return context.getUuidGenerator().generateExchangeUuid();
    }

    @Override
    public final String toString() {
        // do not output information about the message as it may contain sensitive information
        if (exchangeId != null) {
            return "Exchange[" + exchangeId + "]";
        } else {
            return "Exchange[]";
        }
    }

    void setSafeCopyProperty(String key, SafeCopyProperty value) {
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
    <T> T getSafeCopyProperty(String key, Class<T> type) {
        if (!hasSafeCopyProperties()) {
            return null;
        }
        Object value = safeCopyProperties.get(key);

        if (type.isInstance(value)) {
            return (T) value;
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    public ExtendedExchangeExtension getExchangeExtension() {
        return privateExtension;
    }

    private static Map<String, Object> safeCopyProperties(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }
        return new ConcurrentHashMap<>(properties);
    }
}
