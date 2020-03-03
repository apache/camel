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

import java.util.Map;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.HasId;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerAware;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
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
 */
public abstract class DefaultEndpoint extends ServiceSupport implements Endpoint, HasId, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEndpoint.class);

    private final String id = EndpointHelper.createEndpointId();
    private transient String endpointUriToString;
    private volatile String endpointUri;
    private CamelContext camelContext;
    private Component component;
    @UriParam(label = "producer",
            description = "Whether the producer should be started lazy (on the first message). By starting lazy you can use this to allow CamelContext and routes to startup"
                    + " in situations where a producer may otherwise fail during starting and cause the route to fail being started. By deferring this startup to be lazy then"
                    + " the startup failure can be handled during routing messages via Camel's routing error handlers. Beware that when the first message is processed"
                    + " then creating and starting the producer may take a little time and prolong the total processing time of the processing.")
    private boolean lazyStartProducer;
    @UriParam(label = "consumer", description = "Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions occurred while"
                    + " the consumer is trying to pickup incoming messages, or the likes, will now be processed as a message and handled by the routing Error Handler."
                    + " By default the consumer will use the org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or ERROR level and ignored.")
    private boolean bridgeErrorHandler;
    @UriParam(label = "consumer,advanced", optionalPrefix = "consumer.", description = "To let the consumer use a custom ExceptionHandler."
            + " Notice if the option bridgeErrorHandler is enabled then this option is not in use."
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
    @UriParam(label = "advanced",
            description = "Whether the endpoint should use basic property binding (Camel 2.x) or the newer property binding with additional capabilities")
    private boolean basicPropertyBinding;
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

    @Override
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
    @Override
    public String getId() {
        return id;
    }

    @Override
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

    @Override
    public String getEndpointKey() {
        if (isLenientProperties()) {
            // only use the endpoint uri without parameters as the properties are lenient
            String uri = getEndpointUri();
            if (uri.indexOf('?') != -1) {
                return StringHelper.before(uri, "?");
            } else {
                return uri;
            }
        } else {
            // use the full endpoint uri
            return getEndpointUri();
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public AsyncProducer createAsyncProducer() throws Exception {
        if (isLazyStartProducer()) {
            return new LazyStartProducer(this);
        } else {
            return AsyncProcessorConverterHelper.convert(createProducer());
        }
    }

    /**
     * Returns the component that created this endpoint, or <tt>null</tt> if none set.
     */
    public Component getComponent() {
        return component;
    }

    public void setComponent(Component component) {
        this.component = component;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        // should not call configurePollingConsumer when its EventDrivenPollingConsumer
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating EventDrivenPollingConsumer with queueSize: {} blockWhenFull: {} blockTimeout: {}",
                    getPollingConsumerQueueSize(), isPollingConsumerBlockWhenFull(), getPollingConsumerBlockTimeout());
        }
        EventDrivenPollingConsumer consumer = new EventDrivenPollingConsumer(this, getPollingConsumerQueueSize());
        consumer.setBlockWhenFull(isPollingConsumerBlockWhenFull());
        consumer.setBlockTimeout(getPollingConsumerBlockTimeout());
        return consumer;
    }

    @Override
    public Exchange createExchange() {
        return new DefaultExchange(this, getExchangePattern());
    }

    @Override
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
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * Whether the endpoint should use basic property binding (Camel 2.x) or the newer property binding with additional capabilities.
     */
    public boolean isBasicPropertyBinding() {
        return basicPropertyBinding;
    }

    /**
     * Whether the endpoint should use basic property binding (Camel 2.x) or the newer property binding with additional capabilities.
     */
    public void setBasicPropertyBinding(boolean basicPropertyBinding) {
        this.basicPropertyBinding = basicPropertyBinding;
    }

    public boolean isLazyStartProducer() {
        return lazyStartProducer;
    }

    /**
     * Whether the producer should be started lazy (on the first message). By starting lazy you can use this to allow CamelContext and routes to startup
     * in situations where a producer may otherwise fail during starting and cause the route to fail being started. By deferring this startup to be lazy then
     * the startup failure can be handled during routing messages via Camel's routing error handlers. Beware that when the first message is processed
     * then creating and starting the producer may take a little time and prolong the total processing time of the processing.
     */
    public void setLazyStartProducer(boolean lazyStartProducer) {
        this.lazyStartProducer = lazyStartProducer;
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
     * Notice if the option bridgeErrorHandler is enabled then this options is not in use.
     * By default the consumer will deal with exceptions, that will be logged at WARN/ERROR level and ignored.
     */
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Gets the {@link org.apache.camel.PollingConsumer} queue size, when {@link EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link EventDrivenPollingConsumer} implementation.
     * <p/>
     * The default value is <tt>1000</tt>
     */
    public int getPollingConsumerQueueSize() {
        return pollingConsumerQueueSize;
    }

    /**
     * Sets the {@link org.apache.camel.PollingConsumer} queue size, when {@link EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link EventDrivenPollingConsumer} implementation.
     * <p/>
     * The default value is <tt>1000</tt>
     */
    public void setPollingConsumerQueueSize(int pollingConsumerQueueSize) {
        this.pollingConsumerQueueSize = pollingConsumerQueueSize;
    }

    /**
     * Whether to block when adding to the internal queue off when {@link EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link EventDrivenPollingConsumer} implementation.
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
     * Set whether to block when adding to the internal queue off when {@link EventDrivenPollingConsumer}
     * is being used. Notice some Camel components may have their own implementation of {@link org.apache.camel.PollingConsumer} and
     * therefore not using the default {@link EventDrivenPollingConsumer} implementation.
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
     * Sets the timeout in millis to use when adding to the internal queue off when {@link EventDrivenPollingConsumer}
     * is being used.
     *
     * @see #setPollingConsumerBlockWhenFull(boolean)
     */
    public long getPollingConsumerBlockTimeout() {
        return pollingConsumerBlockTimeout;
    }

    /**
     * Sets the timeout in millis to use when adding to the internal queue off when {@link EventDrivenPollingConsumer}
     * is being used.
     *
     * @see #setPollingConsumerBlockWhenFull(boolean)
     */
    public void setPollingConsumerBlockTimeout(long pollingConsumerBlockTimeout) {
        this.pollingConsumerBlockTimeout = pollingConsumerBlockTimeout;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        setProperties(this, options);
    }

    /**
     * Sets the bean properties on the given bean.
     * <p/>
     * This is the same logical implementation as {@link DefaultComponent#setProperties(Object, java.util.Map)}
     *
     * @param bean        the bean
     * @param parameters  properties to set
     */
    public void setProperties(Object bean, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        boolean basic = basicPropertyBinding || "true".equals(parameters.getOrDefault("basicPropertyBinding", "false"));
        if (basic) {
            // use basic binding
            PropertyBindingSupport.build()
                    .withPlaceholder(false).withNesting(false).withDeepNesting(false).withReference(false)
                    .bind(camelContext, bean, parameters);
        } else {
            PropertyConfigurer configurer = null;
            if (bean instanceof Component) {
                configurer = getComponent().getComponentPropertyConfigurer();
            } else if (bean instanceof Endpoint) {
                configurer = getComponent().getEndpointPropertyConfigurer();
            } else if (bean instanceof PropertyConfigurerAware) {
                configurer = ((PropertyConfigurerAware) bean).getPropertyConfigurer(bean);
            }
            // use advanced binding
            PropertyBindingSupport.build().withConfigurer(configurer).bind(camelContext, bean, parameters);
        }
    }

    /**
     * A factory method to lazily create the endpointUri if none is specified
     */
    protected String createEndpointUri() {
        return null;
    }

    /**
     * Sets the endpointUri if it has not been specified yet via some kind of
     * dependency injection mechanism. This allows dependency injection
     * frameworks such as Spring to set the default endpoint URI in
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

    /**
     * An endpoint should favour to be singleton by default, only in some rare special cases
     * can an endpoint be non-singleton. This implementation is singleton and this method returns true.
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        // default should be false for most components
        return false;
    }

    protected void configureConsumer(Consumer consumer) throws Exception {
        // inject CamelContext
        if (consumer instanceof CamelContextAware) {
            ((CamelContextAware) consumer).setCamelContext(getCamelContext());
        }

        if (bridgeErrorHandler) {
            if (consumer instanceof DefaultConsumer) {
                DefaultConsumer defaultConsumer = (DefaultConsumer) consumer;
                defaultConsumer.setExceptionHandler(new BridgeExceptionHandlerToErrorHandler(defaultConsumer));
            } else {
                throw new IllegalArgumentException("Option bridgeErrorHandler is only supported by endpoints,"
                        + " having their consumer extend DefaultConsumer. The consumer is a " + consumer.getClass().getName() + " class.");
            }
        }
        if (exceptionHandler != null) {
            if (consumer instanceof DefaultConsumer) {
                DefaultConsumer defaultConsumer = (DefaultConsumer) consumer;
                defaultConsumer.setExceptionHandler(exceptionHandler);
            }
        }
    }

    protected void configurePollingConsumer(PollingConsumer consumer) throws Exception {
        configureConsumer(consumer);
    }

    @Override
    protected void doInit() throws Exception {
        // noop
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
