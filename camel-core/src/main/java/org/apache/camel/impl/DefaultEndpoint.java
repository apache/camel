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
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.HasId;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default endpoint useful for implementation inheritance.
 * <p/>
 * Components which leverages <a
 * href="http://camel.apache.org/asynchronous-routing-engine.html">asynchronous
 * processing model</a> should check the {@link #isSynchronous()} to determine
 * if asynchronous processing is allowed. The <tt>synchronous</tt> option on the
 * endpoint allows Camel end users to dictate whether they want the asynchronous
 * model or not. The option is default <tt>false</tt> which means asynchronous
 * processing is allowed.
 * 
 * @version
 */
public abstract class DefaultEndpoint extends ServiceSupport implements Endpoint, HasId, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEndpoint.class);
    private final String id = EndpointHelper.createEndpointId();
    private transient String endpointUriToString;
    private String endpointUri;
    private EndpointConfiguration endpointConfiguration;
    private CamelContext camelContext;
    private Component component;
    @UriParam(label = "consumer", optionalPrefix = "consumer.", description = "Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions occurred while"
                    + " the consumer is trying to pickup incoming messages, or the likes, will now be processed as a message and handled by the routing Error Handler."
                    + " By default the consumer will use the org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or ERROR level and ignored.")
    private boolean bridgeErrorHandler;
    @UriParam(label = "consumer,advanced", optionalPrefix = "consumer.", description = "To let the consumer use a custom ExceptionHandler."
            + " Notice if the option bridgeErrorHandler is enabled then this options is not in use."
            + " By default the consumer will deal with exceptions, that will be logged at WARN or ERROR level and ignored.")
    private ExceptionHandler exceptionHandler;
    @UriParam(label = "consumer,advanced",
            description = "Sets the exchange pattern when the consumer creates an exchange.")
    // no default value set on @UriParam as the MEP is sometimes InOnly or InOut depending on the component in use
    private ExchangePattern exchangePattern = ExchangePattern.InOnly;
    // option to allow end user to dictate whether async processing should be
    // used or not (if possible)
    @UriParam(defaultValue = "false", label = "advanced",
            description = "Sets whether synchronous processing should be strictly used, or Camel is allowed to use asynchronous processing (if supported).")
    private boolean synchronous;
    // these options are not really in use any option related to the consumer has a specific option on the endpoint
    // and consumerProperties was added from the very start of Camel.
    private Map<String, Object> consumerProperties;
    // pooling consumer options only related to EventDrivenPollingConsumer which are very seldom in use
    // so lets not expose them in the component docs as it will be included in every component
    private int pollingConsumerQueueSize = 1000;
    private boolean pollingConsumerBlockWhenFull = true;
    private long pollingConsumerBlockTimeout;

    /**
     * Constructs a fully-initialized DefaultEndpoint instance. This is the
     * preferred method of constructing an object from Java code (as opposed to
     * Spring beans, etc.).
     * 
     * @param endpointUri the full URI used to create this endpoint
     * @param component the component that created this endpoint
     */
    protected DefaultEndpoint(String endpointUri, Component component) {
        this.camelContext = component == null ? null : component.getCamelContext();
        this.component = component;
        this.setEndpointUri(endpointUri);
    }

    /**
     * Constructs a DefaultEndpoint instance which has <b>not</b> been created
     * using a {@link Component}.
     * <p/>
     * <b>Note:</b> It is preferred to create endpoints using the associated
     * component.
     * 
     * @param endpointUri the full URI used to create this endpoint
     * @param camelContext the Camel Context in which this endpoint is operating
     */
    @Deprecated
    protected DefaultEndpoint(String endpointUri, CamelContext camelContext) {
        this(endpointUri);
        this.camelContext = camelContext;
    }

    /**
     * Constructs a partially-initialized DefaultEndpoint instance.
     * <p/>
     * <b>Note:</b> It is preferred to create endpoints using the associated
     * component.
     * 
     * @param endpointUri the full URI used to create this endpoint
     */
    @Deprecated
    protected DefaultEndpoint(String endpointUri) {
        this.setEndpointUri(endpointUri);
    }

    /**
     * Constructs a partially-initialized DefaultEndpoint instance. Useful when
     * creating endpoints manually (e.g., as beans in Spring).
     * <p/>
     * Please note that the endpoint URI must be set through properties (or
     * overriding {@link #createEndpointUri()} if one uses this constructor.
     * <p/>
     * <b>Note:</b> It is preferred to create endpoints using the associated
     * component.
     */
    protected DefaultEndpoint() {
    }

    public int hashCode() {
        return getEndpointUri().hashCode() * 37 + 1;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DefaultEndpoint) {
            DefaultEndpoint that = (DefaultEndpoint)object;
            // must also match the same CamelContext in case we compare endpoints from different contexts
            String thisContextName = this.getCamelContext() != null ? this.getCamelContext().getName() : null;
            String thatContextName = that.getCamelContext() != null ? that.getCamelContext().getName() : null;
            return ObjectHelper.equal(this.getEndpointUri(), that.getEndpointUri()) && ObjectHelper.equal(thisContextName, thatContextName);
        }
        return false;
    }

    @Override
    public String toString() {
        if (endpointUriToString == null) {
            String value = null;
            try {
                value = getEndpointUri();
            } catch (RuntimeException e) {
                // ignore any exception and use null for building the string value
            }
            // ensure to sanitize uri so we do not show sensitive information such as passwords
            endpointUriToString = URISupport.sanitizeUri(value);
        }
        return endpointUriToString;
    }

    /**
     * Returns a unique String ID which can be used for aliasing without having
     * to use the whole URI which is not unique
     */
    public String getId() {
        return id;
    }

    public String getEndpointUri() {
        if (endpointUri == null) {
            endpointUri = createEndpointUri();
            if (endpointUri == null) {
                throw new IllegalArgumentException("endpointUri is not specified and " + getClass().getName()
                    + " does not implement createEndpointUri() to create a default value");
            }
        }
        return endpointUri;
    }

    public EndpointConfiguration getEndpointConfiguration() {
        if (endpointConfiguration == null) {
            endpointConfiguration = createEndpointConfiguration(getEndpointUri());
        }
        return endpointConfiguration;
    }

    /**
     * Sets a custom {@link EndpointConfiguration}
     *
     * @param endpointConfiguration a custom endpoint configuration to be used.
     */
    @Deprecated
    public void setEndpointConfiguration(EndpointConfiguration endpointConfiguration) {
        this.endpointConfiguration = endpointConfiguration;
    }

    public String getEndpointKey() {
        if (isLenientProperties()) {
            // only use the endpoint uri without parameters as the properties is
            // lenient
            String uri = getEndpointUri();
            if (uri.indexOf('?') != -1) {
                return ObjectHelper.before(uri, "?");
            } else {
                return uri;
            }
        } else {
            // use the full endpoint uri
            return getEndpointUri();
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Returns the component that created this endpoint.
     * 
     * @return the component that created this endpoint, or <tt>null</tt> if
     *         none set
     */
    public Component getComponent() {
        return component;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public PollingConsumer createPollingConsumer() throws Exception {
        // should not call configurePollingConsumer when its EventDrivenPollingConsumer
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating EventDrivenPollingConsumer with queueSize: {} blockWhenFull: {} blockTimeout: {}",
                    new Object[]{getPollingConsumerQueueSize(), isPollingConsumerBlockWhenFull(), getPollingConsumerBlockTimeout()});
        }
        EventDrivenPollingConsumer consumer = new EventDrivenPollingConsumer(this, getPollingConsumerQueueSize());
        consumer.setBlockWhenFull(isPollingConsumerBlockWhenFull());
        consumer.setBlockTimeout(getPollingConsumerBlockTimeout());
        return consumer;
    }

    public Exchange createExchange(Exchange exchange) {
        return exchange.copy();
    }

    public Exchange createExchange() {
        return createExchange(getExchangePattern());
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return new DefaultExchange(this, pattern);
    }

    /**
     * Returns the default exchange pattern to use when creating an exchange.
     */
    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    /**
     * Sets the default exchange pattern when creating an exchange.
     */
    public void setExchangePattern(ExchangePattern exchangePattern) {
        this.exchangePattern = exchangePattern;
    }

    /**
     * Returns whether synchronous processing should be strictly used.
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Sets whether synchronous processing should be strictly used, or Camel is
     * allowed to use asynchronous processing (if supported).
     * 
     * @param synchronous <tt>true</tt> to enforce synchronous processing
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public boolean isBridgeErrorHandler() {
        return bridgeErrorHandler;
    }

    /**
     * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions occurred while
     * the consumer is trying to pickup incoming messages, or the likes, will now be processed as a message and
     * handled by the routing Error Handler.
     * <p/>
     * By default the consumer will use the org.apache.camel.spi.ExceptionHandler to deal with exceptions,
     * that will be logged at WARN/ERROR level and ignored.
     */
    public void setBridgeErrorHandler(boolean bridgeErrorHandler) {
        this.bridgeErrorHandler = bridgeErrorHandler;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * To let the consumer use a custom ExceptionHandler.
     + Notice if the option bridgeErrorHandler is enabled then this options is not in use.
     + By default the consumer will deal with exceptions, that will be logged at WARN/ERROR level and ignored.
     */
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Gets the {@link org.apache.camel.PollingConsumer} queue size, when {@link org.apache.camel.impl.EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link org.apache.camel.impl.EventDrivenPollingConsumer} implementation.
     * <p/>
     * The default value is <tt>1000</tt>
     */
    public int getPollingConsumerQueueSize() {
        return pollingConsumerQueueSize;
    }

    /**
     * Sets the {@link org.apache.camel.PollingConsumer} queue size, when {@link org.apache.camel.impl.EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link org.apache.camel.impl.EventDrivenPollingConsumer} implementation.
     * <p/>
     * The default value is <tt>1000</tt>
     */
    public void setPollingConsumerQueueSize(int pollingConsumerQueueSize) {
        this.pollingConsumerQueueSize = pollingConsumerQueueSize;
    }

    /**
     * Whether to block when adding to the internal queue off when {@link org.apache.camel.impl.EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link org.apache.camel.impl.EventDrivenPollingConsumer} implementation.
     * <p/>
     * Setting this option to <tt>false</tt>, will result in an {@link java.lang.IllegalStateException} being thrown
     * when trying to add to the queue, and its full.
     * <p/>
     * The default value is <tt>true</tt> which will block the producer queue until the queue has space.
     */
    public boolean isPollingConsumerBlockWhenFull() {
        return pollingConsumerBlockWhenFull;
    }

    /**
     * Set whether to block when adding to the internal queue off when {@link org.apache.camel.impl.EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link org.apache.camel.impl.EventDrivenPollingConsumer} implementation.
     * <p/>
     * Setting this option to <tt>false</tt>, will result in an {@link java.lang.IllegalStateException} being thrown
     * when trying to add to the queue, and its full.
     * <p/>
     * The default value is <tt>true</tt> which will block the producer queue until the queue has space.
     */
    public void setPollingConsumerBlockWhenFull(boolean pollingConsumerBlockWhenFull) {
        this.pollingConsumerBlockWhenFull = pollingConsumerBlockWhenFull;
    }

    /**
     * Sets the timeout in millis to use when adding to the internal queue off when {@link org.apache.camel.impl.EventDrivenPollingConsumer}
     * is being used.
     *
     * @see #setPollingConsumerBlockWhenFull(boolean)
     */
    public long getPollingConsumerBlockTimeout() {
        return pollingConsumerBlockTimeout;
    }

    /**
     * Sets the timeout in millis to use when adding to the internal queue off when {@link org.apache.camel.impl.EventDrivenPollingConsumer}
     * is being used.
     *
     * @see #setPollingConsumerBlockWhenFull(boolean)
     */
    public void setPollingConsumerBlockTimeout(long pollingConsumerBlockTimeout) {
        this.pollingConsumerBlockTimeout = pollingConsumerBlockTimeout;
    }

    public void configureProperties(Map<String, Object> options) {
        Map<String, Object> consumerProperties = IntrospectionSupport.extractProperties(options, "consumer.");
        if (consumerProperties != null && !consumerProperties.isEmpty()) {
            setConsumerProperties(consumerProperties);
        }
    }

    /**
     * Sets the bean properties on the given bean.
     * <p/>
     * This is the same logical implementation as {@link DefaultComponent#setProperties(Object, java.util.Map)}
     *
     * @param bean  the bean
     * @param parameters  properties to set
     */
    protected void setProperties(Object bean, Map<String, Object> parameters) throws Exception {
        // set reference properties first as they use # syntax that fools the regular properties setter
        EndpointHelper.setReferenceProperties(getCamelContext(), bean, parameters);
        EndpointHelper.setProperties(getCamelContext(), bean, parameters);
    }

    /**
     * A factory method to lazily create the endpointUri if none is specified
     */
    protected String createEndpointUri() {
        return null;
    }

    /**
     * A factory method to lazily create the endpoint configuration if none is specified
     */
    @Deprecated
    protected EndpointConfiguration createEndpointConfiguration(String uri) {
        // using this factory method to be backwards compatible with the old code
        if (getComponent() != null) {
            // prefer to use component endpoint configuration
            try {
                return getComponent().createConfiguration(uri);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        } else if (getCamelContext() != null) {
            // fallback and use a mapped endpoint configuration
            return new MappedEndpointConfiguration(getCamelContext(), uri);
        }
        // not configuration possible
        return null;
    }

    /**
     * Sets the endpointUri if it has not been specified yet via some kind of
     * dependency injection mechanism. This allows dependency injection
     * frameworks such as Spring or Guice to set the default endpoint URI in
     * cases where it has not been explicitly configured using the name/context
     * in which an Endpoint is created.
     */
    public void setEndpointUriIfNotSpecified(String value) {
        if (endpointUri == null) {
            setEndpointUri(value);
        }
    }

    /**
     * Sets the URI that created this endpoint.
     */
    protected void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    public boolean isLenientProperties() {
        // default should be false for most components
        return false;
    }

    public Map<String, Object> getConsumerProperties() {
        if (consumerProperties == null) {
            // must create empty if none exists
            consumerProperties = new HashMap<String, Object>();
        }
        return consumerProperties;
    }

    public void setConsumerProperties(Map<String, Object> consumerProperties) {
        // append consumer properties
        if (consumerProperties != null && !consumerProperties.isEmpty()) {
            if (this.consumerProperties == null) {
                this.consumerProperties = new HashMap<String, Object>(consumerProperties);
            } else {
                this.consumerProperties.putAll(consumerProperties);
            }
        }
    }

    protected void configureConsumer(Consumer consumer) throws Exception {
        // inject CamelContext
        if (consumer instanceof CamelContextAware) {
            ((CamelContextAware) consumer).setCamelContext(getCamelContext());
        }

        if (consumerProperties != null) {
            // use a defensive copy of the consumer properties as the methods below will remove the used properties
            // and in case we restart routes, we need access to the original consumer properties again
            Map<String, Object> copy = new HashMap<String, Object>(consumerProperties);

            // set reference properties first as they use # syntax that fools the regular properties setter
            EndpointHelper.setReferenceProperties(getCamelContext(), consumer, copy);
            EndpointHelper.setProperties(getCamelContext(), consumer, copy);

            // special consumer.bridgeErrorHandler option
            Object bridge = copy.remove("bridgeErrorHandler");
            if (bridge != null && "true".equals(bridge)) {
                if (consumer instanceof DefaultConsumer) {
                    DefaultConsumer defaultConsumer = (DefaultConsumer) consumer;
                    defaultConsumer.setExceptionHandler(new BridgeExceptionHandlerToErrorHandler(defaultConsumer));
                } else {
                    throw new IllegalArgumentException("Option consumer.bridgeErrorHandler is only supported by endpoints,"
                            + " having their consumer extend DefaultConsumer. The consumer is a " + consumer.getClass().getName() + " class.");
                }
            }

            if (!this.isLenientProperties() && copy.size() > 0) {
                throw new ResolveEndpointFailedException(this.getEndpointUri(), "There are " + copy.size()
                    + " parameters that couldn't be set on the endpoint consumer."
                    + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                    + " Unknown consumer parameters=[" + copy + "]");
            }
        }
    }

    protected void configurePollingConsumer(PollingConsumer consumer) throws Exception {
        configureConsumer(consumer);
    }

    @Override
    protected void doStart() throws Exception {
        // the bridgeErrorHandler/exceptionHandler was originally configured with consumer. prefix, such as consumer.bridgeErrorHandler=true
        // so if they have been configured on the endpoint then map to the old naming style
        if (bridgeErrorHandler) {
            getConsumerProperties().put("bridgeErrorHandler", "true");
        }
        if (exceptionHandler != null) {
            getConsumerProperties().put("exceptionHandler", exceptionHandler);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
