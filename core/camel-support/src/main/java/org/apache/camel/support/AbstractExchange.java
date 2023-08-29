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
import java.util.HashSet;
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
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.trait.message.MessageTrait;
import org.apache.camel.trait.message.RedeliveryTraitPayload;
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
class AbstractExchange implements Exchange {
    protected final EnumMap<ExchangePropertyKey, Object> internalProperties;

    protected final CamelContext context;
    protected Map<String, Object> properties; // create properties on-demand as we use internal properties mostly
    protected long created;
    protected Message in;
    protected Message out;
    protected Exception exception;
    protected String exchangeId;
    protected ExchangePattern pattern;
    protected boolean routeStop;
    protected boolean rollbackOnly;
    protected boolean rollbackOnlyLast;
    protected Map<String, SafeCopyProperty> safeCopyProperties;
    private final ExtendedExchangeExtension privateExtension;
    private RedeliveryTraitPayload externalRedelivered = RedeliveryTraitPayload.UNDEFINED_REDELIVERY;

    AbstractExchange(CamelContext context, EnumMap<ExchangePropertyKey, Object> internalProperties,
                     Map<String, Object> properties) {
        this.context = context;
        this.internalProperties = new EnumMap<>(internalProperties);
        this.privateExtension = new ExtendedExchangeExtension(this);
        this.properties = properties;
    }

    public AbstractExchange(CamelContext context) {
        this(context, ExchangePattern.InOnly);
    }

    public AbstractExchange(CamelContext context, ExchangePattern pattern) {
        this.context = context;
        this.pattern = pattern;
        this.created = System.currentTimeMillis();

        internalProperties = new EnumMap<>(ExchangePropertyKey.class);
        privateExtension = new ExtendedExchangeExtension(this);
    }

    public AbstractExchange(Exchange parent) {
        this.context = parent.getContext();
        this.pattern = parent.getPattern();
        this.created = parent.getCreated();

        internalProperties = new EnumMap<>(ExchangePropertyKey.class);

        privateExtension = new ExtendedExchangeExtension(this);
        privateExtension.setFromEndpoint(parent.getFromEndpoint());
        privateExtension.setFromRouteId(parent.getFromRouteId());
        privateExtension.setUnitOfWork(parent.getUnitOfWork());
    }

    public AbstractExchange(Endpoint fromEndpoint) {
        this.context = fromEndpoint.getCamelContext();
        this.pattern = fromEndpoint.getExchangePattern();
        this.created = System.currentTimeMillis();

        internalProperties = new EnumMap<>(ExchangePropertyKey.class);
        privateExtension = new ExtendedExchangeExtension(this);
        privateExtension.setFromEndpoint(fromEndpoint);
    }

    public AbstractExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        this.context = fromEndpoint.getCamelContext();
        this.pattern = pattern;
        this.created = System.currentTimeMillis();

        internalProperties = new EnumMap<>(ExchangePropertyKey.class);
        privateExtension = new ExtendedExchangeExtension(this);
        privateExtension.setFromEndpoint(fromEndpoint);
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
        final ExtendedExchangeExtension newExchangeExtension = exchange.getExchangeExtension();
        newExchangeExtension.setNotifyEvent(getExchangeExtension().isNotifyEvent());
        newExchangeExtension.setRedeliveryExhausted(getExchangeExtension().isRedeliveryExhausted());
        newExchangeExtension.setErrorHandlerHandled(getExchangeExtension().getErrorHandlerHandled());
        newExchangeExtension.setStreamCacheDisabled(getExchangeExtension().isStreamCacheDisabled());

        // copy properties after body as body may trigger lazy init
        if (hasProperties()) {
            copyProperties(getProperties(), exchange.getProperties());
        }

        if (hasSafeCopyProperties()) {
            safeCopyProperties(this.safeCopyProperties, exchange.getSafeCopyProperties());
        }
        // copy over internal properties
        exchange.internalProperties.putAll(internalProperties);

        if (getContext().isMessageHistory()) {
            exchange.internalProperties.computeIfPresent(ExchangePropertyKey.MESSAGE_HISTORY,
                    (k, v) -> new CopyOnWriteArrayList<>((List<MessageHistory>) v));
        }

        return exchange;
    }

    private Map<String, Object> safeCopyHeaders(Map<String, Object> headers) {
        if (headers == null) {
            return null;
        }

        if (context != null) {
            HeadersMapFactory factory = context.getCamelContextExtension().getHeadersMapFactory();
            if (factory != null) {
                return factory.newMap(headers);
            }
        }
        // should not really happen but some tests dont start camel context
        return new HashMap<>(headers);
    }

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
    public String toString() {
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
        Object value = getSafeCopyProperties().get(key);

        if (type.isInstance(value)) {
            return (T) value;
        }

        return ExchangeHelper.convertToType(this, type, value);
    }

    public ExtendedExchangeExtension getExchangeExtension() {
        return privateExtension;
    }
}
