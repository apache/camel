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
package org.apache.camel.processor;

import java.util.Map;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.support.ExchangeHelper.copyResultsPreservePattern;

/**
 * A content enricher that enriches input data by first obtaining additional data from a <i>resource</i> represented by
 * an endpoint <code>producer</code> and second by aggregating input data and additional data. Aggregation of input data
 * and additional data is delegated to an {@link AggregationStrategy} object.
 * <p/>
 * Uses a {@link org.apache.camel.Producer} to obtain the additional data as opposed to {@link PollEnricher} that uses a
 * {@link org.apache.camel.PollingConsumer}.
 *
 * @see PollEnricher
 */
public class Enricher extends AsyncProcessorSupport implements IdAware, RouteIdAware, CamelContextAware {

    private CamelContext camelContext;
    private String id;
    private String routeId;
    private final Expression expression;
    private final String uri;
    private String variableSend;
    private String variableReceive;
    private AggregationStrategy aggregationStrategy;
    private boolean aggregateOnException;
    private boolean shareUnitOfWork;
    private int cacheSize;
    private boolean ignoreInvalidEndpoint;
    private boolean allowOptimisedComponents = true;
    private boolean autoStartupComponents = true;
    private HeadersMapFactory headersMapFactory;
    private ProcessorExchangeFactory processorExchangeFactory;
    private SendDynamicProcessor sendDynamicProcessor;

    public Enricher(Expression expression, String uri) {
        this.expression = expression;
        this.uri = uri;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public Expression getExpression() {
        return expression;
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return sendDynamicProcessor.getEndpointUtilizationStatistics();
    }

    public String getVariableSend() {
        return variableSend;
    }

    public void setVariableSend(String variableSend) {
        this.variableSend = variableSend;
    }

    public String getVariableReceive() {
        return variableReceive;
    }

    public void setVariableReceive(String variableReceive) {
        this.variableReceive = variableReceive;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public boolean isAggregateOnException() {
        return aggregateOnException;
    }

    public void setAggregateOnException(boolean aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
    }

    public boolean isShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(boolean shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public boolean isIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(boolean ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    public boolean isAllowOptimisedComponents() {
        return allowOptimisedComponents;
    }

    public void setAllowOptimisedComponents(boolean allowOptimisedComponents) {
        this.allowOptimisedComponents = allowOptimisedComponents;
    }

    public boolean isAutoStartupComponents() {
        return autoStartupComponents;
    }

    public void setAutoStartupComponents(boolean autoStartupComponents) {
        this.autoStartupComponents = autoStartupComponents;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Exchange resourceExchange = createResourceExchange(exchange, ExchangePattern.InOut);

        // if we should store the received message body in a variable,
        // then we need to preserve the original message body
        Object body = null;
        Map<String, Object> headers = null;
        if (variableReceive != null) {
            try {
                body = exchange.getMessage().getBody();
                // do a defensive copy of the headers
                headers = headersMapFactory.newMap(exchange.getMessage().getHeaders());
            } catch (Exception throwable) {
                exchange.setException(throwable);
                callback.done(true);
                return true;
            }
        }
        final Object originalBody = body;
        final Map<String, Object> originalHeaders = headers;

        return sendDynamicProcessor.process(resourceExchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                if (!isAggregateOnException() && resourceExchange.isFailed()) {
                    // copy resource exchange onto original exchange (preserving pattern)
                    copyResultsWithoutCorrelationId(exchange, resourceExchange);
                } else {
                    prepareResult(exchange);
                    try {
                        // prepare the exchanges for aggregation
                        ExchangeHelper.prepareAggregation(exchange, resourceExchange);
                        MessageHelper.resetStreamCache(exchange.getIn());

                        Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
                        if (aggregatedExchange != null) {
                            if (variableReceive != null) {
                                // result should be stored in variable instead of message body
                                ExchangeHelper.setVariableFromMessageBodyAndHeaders(exchange, variableReceive,
                                        exchange.getMessage());
                                exchange.getMessage().setBody(originalBody);
                                exchange.getMessage().setHeaders(originalHeaders);
                            }
                            // copy aggregation result onto original exchange (preserving pattern)
                            copyResultsWithoutCorrelationId(exchange, aggregatedExchange);
                            // handover any synchronization (if unit of work is not shared)
                            if (resourceExchange != null && !isShareUnitOfWork()) {
                                resourceExchange.getExchangeExtension().handoverCompletions(exchange);
                            }
                        }
                    } catch (Exception e) {
                        // if the aggregationStrategy threw an exception, set it on the original exchange
                        exchange.setException(new CamelExchangeException("Error occurred during aggregation", exchange, e));
                    }
                }

                // and release resource exchange back in pool
                processorExchangeFactory.release(resourceExchange);

                callback.done(doneSync);
            }
        });
    }

    /**
     * Creates a new {@link DefaultExchange} instance from the given <code>exchange</code>. The resulting exchange's
     * pattern is defined by <code>pattern</code>.
     *
     * @param  source  exchange to copy from.
     * @param  pattern exchange pattern to set.
     * @return         created exchange.
     */
    protected Exchange createResourceExchange(Exchange source, ExchangePattern pattern) {
        // copy exchange, and do not share the unit of work
        Exchange target = processorExchangeFactory.createCorrelatedCopy(source, false);
        target.setPattern(pattern);

        // if we share unit of work, we need to prepare the resource exchange
        if (isShareUnitOfWork()) {
            target.setProperty(ExchangePropertyKey.PARENT_UNIT_OF_WORK, source.getUnitOfWork());
            // and then share the unit of work
            target.getExchangeExtension().setUnitOfWork(source.getUnitOfWork());
        }
        return target;
    }

    private static void prepareResult(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().copyFrom(exchange.getIn());
        }
    }

    private static AggregationStrategy defaultAggregationStrategy() {
        return new CopyAggregationStrategy();
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    protected void doBuild() throws Exception {
        // use send dynamic to send to endpoint
        this.sendDynamicProcessor = new SendDynamicProcessor(uri, expression);
        this.sendDynamicProcessor.setCamelContext(camelContext);
        this.sendDynamicProcessor.setCacheSize(cacheSize);
        this.sendDynamicProcessor.setIgnoreInvalidEndpoint(ignoreInvalidEndpoint);
        this.sendDynamicProcessor.setAllowOptimisedComponents(allowOptimisedComponents);
        this.sendDynamicProcessor.setAutoStartupComponents(autoStartupComponents);
        this.sendDynamicProcessor.setVariableSend(variableSend);

        // create a per processor exchange factory
        this.processorExchangeFactory = getCamelContext().getCamelContextExtension()
                .getProcessorExchangeFactory().newProcessorExchangeFactory(this);
        this.processorExchangeFactory.setRouteId(getRouteId());
        this.processorExchangeFactory.setId(getId());

        if (aggregationStrategy == null) {
            aggregationStrategy = defaultAggregationStrategy();
        }
        CamelContextAware.trySetCamelContext(aggregationStrategy, camelContext);
        ServiceHelper.buildService(processorExchangeFactory, sendDynamicProcessor);
    }

    @Override
    protected void doInit() throws Exception {
        headersMapFactory = camelContext.getCamelContextExtension().getHeadersMapFactory();
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processorExchangeFactory, aggregationStrategy, sendDynamicProcessor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(aggregationStrategy, processorExchangeFactory, sendDynamicProcessor);
    }

    private static void copyResultsWithoutCorrelationId(Exchange target, Exchange source) {
        Object correlationId = target.removeProperty(ExchangePropertyKey.CORRELATION_ID);
        copyResultsPreservePattern(target, source);
        if (correlationId != null) {
            target.setProperty(ExchangePropertyKey.CORRELATION_ID, correlationId);
        }
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (newExchange != null) {
                copyResultsWithoutCorrelationId(oldExchange, newExchange);
            }
            return oldExchange;
        }

    }

}
