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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PooledExchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckAware;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default consumer useful for implementation inheritance.
 */
public class DefaultConsumer extends ServiceSupport implements Consumer, RouteAware, RouteIdAware, HealthCheckAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConsumer.class);

    private transient String consumerToString;
    private final Endpoint endpoint;
    private final Processor processor;
    private final AsyncProcessor asyncProcessor;
    private final ExchangeFactory exchangeFactory;
    private HealthCheck healthCheck;
    private ExceptionHandler exceptionHandler;
    private Route route;
    private String routeId;

    public DefaultConsumer(Endpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
        this.asyncProcessor = AsyncProcessorConverterHelper.convert(processor);
        this.exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), getClass());
        // create a per consumer exchange factory
        this.exchangeFactory = endpoint.getCamelContext().getCamelContextExtension()
                .getExchangeFactory().newExchangeFactory(this);
    }

    @Override
    public String toString() {
        if (consumerToString == null) {
            consumerToString = "Consumer[" + URISupport.sanitizeUri(endpoint.getEndpointUri()) + "]";
        }
        return consumerToString;
    }

    @Override
    public Route getRoute() {
        return route;
    }

    @Override
    public void setRoute(Route route) {
        this.route = route;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    /**
     * If the consumer needs to defer done the {@link org.apache.camel.spi.UnitOfWork} on the processed {@link Exchange}
     * then this method should be use to create and start the {@link UnitOfWork} on the exchange.
     *
     * @param  exchange  the exchange
     * @return           the created and started unit of work
     * @throws Exception is thrown if error starting the unit of work
     * @see              #doneUoW(org.apache.camel.Exchange)
     */
    public UnitOfWork createUoW(Exchange exchange) throws Exception {
        // if the exchange doesn't have from route id set, then set it if it originated
        // from this unit of work
        if (route != null && exchange.getFromRouteId() == null) {
            exchange.getExchangeExtension().setFromRouteId(route.getId());
        }

        // create uow (however for pooled exchanges then the uow is pre-created)
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow == null) {
            uow = PluginHelper.getUnitOfWorkFactory(endpoint.getCamelContext()).createUnitOfWork(exchange);
            exchange.getExchangeExtension().setUnitOfWork(uow);
        }
        return uow;
    }

    /**
     * If the consumer needs to defer done the {@link org.apache.camel.spi.UnitOfWork} on the processed {@link Exchange}
     * then this method should be executed when the consumer is finished processing the message.
     *
     * @param exchange the exchange
     * @see            #createUoW(org.apache.camel.Exchange)
     */
    public void doneUoW(Exchange exchange) {
        UnitOfWorkHelper.doneUow(exchange.getUnitOfWork(), exchange);
    }

    @Override
    public Exchange createExchange(boolean autoRelease) {
        Exchange answer = exchangeFactory.create(getEndpoint(), autoRelease);
        endpoint.configureExchange(answer);

        answer.getExchangeExtension().setFromRouteId(routeId);
        return answer;
    }

    @Override
    public void releaseExchange(Exchange exchange, boolean autoRelease) {
        if (exchange != null) {
            if (!autoRelease && exchange instanceof PooledExchange) {
                // if not auto release we must manually force done
                ((PooledExchange) exchange).done();
            }
            exchangeFactory.release(exchange);
        }
    }

    @Override
    public AsyncCallback defaultConsumerCallback(Exchange exchange, boolean autoRelease) {
        boolean pooled = exchangeFactory.isPooled();
        if (pooled) {
            AsyncCallback answer = exchange.getExchangeExtension().getDefaultConsumerCallback();
            if (answer == null) {
                answer = new DefaultConsumerCallback(this, exchange, autoRelease);
                exchange.getExchangeExtension().setDefaultConsumerCallback(answer);
            }
            return answer;
        } else {
            return new DefaultConsumerCallback(this, exchange, autoRelease);
        }
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Provides an {@link org.apache.camel.AsyncProcessor} interface to the configured processor on the consumer. If the
     * processor does not implement the interface, it will be adapted so that it does.
     */
    public AsyncProcessor getAsyncProcessor() {
        return asyncProcessor;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void setHealthCheck(HealthCheck healthCheck) {
        this.healthCheck = healthCheck;
    }

    @Override
    public HealthCheck getHealthCheck() {
        return healthCheck;
    }

    @Override
    protected void doBuild() throws Exception {
        LOG.debug("Build consumer: {}", this);
        ServiceHelper.buildService(exchangeFactory, processor);

        // force creating and load the class during build time so the JVM does not
        // load the class on first exchange to be created
        Object dummy = new DefaultConsumerCallback(this, null, false);
        LOG.trace("Warming up DefaultConsumer loaded class: {}", dummy.getClass().getName());
    }

    @Override
    protected void doInit() throws Exception {
        LOG.debug("Init consumer: {}", this);
        ServiceHelper.initService(exchangeFactory, processor);
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Starting consumer: {}", this);
        exchangeFactory.setRouteId(routeId);
        ServiceHelper.startService(exchangeFactory, processor);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping consumer: {}", this);
        ServiceHelper.stopService(exchangeFactory, processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        LOG.debug("Shutting down consumer: {}", this);
        ServiceHelper.stopAndShutdownServices(exchangeFactory, processor);
    }

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     *
     * @param t the exception to handle
     */
    protected void handleException(Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(newt);
    }

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     *
     * @param message additional message about the exception
     * @param t       the exception to handle
     */
    protected void handleException(String message, Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(message, newt);
    }

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     *
     * @param message  additional message about the exception
     * @param exchange exchange which cause the exception
     * @param t        the exception to handle
     */
    protected void handleException(String message, Exchange exchange, Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(message, exchange, newt);
    }

    private static final class DefaultConsumerCallback implements AsyncCallback {

        private final DefaultConsumer consumer;
        private final Exchange exchange;
        private final boolean autoRelease;

        public DefaultConsumerCallback(DefaultConsumer consumer, Exchange exchange, boolean autoRelease) {
            this.consumer = consumer;
            this.exchange = exchange;
            this.autoRelease = autoRelease;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                // handle any thrown exception
                if (exchange.getException() != null) {
                    consumer.getExceptionHandler().handleException("Error processing exchange", exchange,
                            exchange.getException());
                }
            } finally {
                if (!autoRelease) {
                    // must release if not auto released
                    consumer.releaseExchange(exchange, autoRelease);
                }
            }
        }

        @Override
        public String toString() {
            return "DefaultConsumerCallback";
        }
    }

}
