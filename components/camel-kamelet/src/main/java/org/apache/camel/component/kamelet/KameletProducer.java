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
package org.apache.camel.component.kamelet;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.processor.transformer.DataTypeProcessor;
import org.apache.camel.processor.transformer.TypeConverterTransformer;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KameletProducer extends DefaultAsyncProducer implements RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(KameletProducer.class);

    // the consumer and the state counter of the component when the consumer was looked up, kept together as several
    // threads can send with this producer at the same time
    private volatile CachedConsumer cachedConsumer;

    private final KameletEndpoint endpoint;
    private final KameletComponent component;
    private final String key;
    private final boolean block;
    private final long timeout;
    private final boolean sink;
    private String routeId;
    boolean registerKamelets;
    // the output data type of the source Kamelet whose route sends to this kamelet:sink, looked up on the first message
    private volatile Processor sourceOutputType;
    private volatile boolean sourceOutputTypeResolved;

    public KameletProducer(KameletEndpoint endpoint, String key) {
        super(endpoint);
        this.endpoint = endpoint;
        this.component = endpoint.getComponent();
        this.key = key;
        this.block = endpoint.isBlock();
        this.timeout = endpoint.getTimeout();
        this.sink = getEndpoint().getEndpointKey().startsWith("kamelet://sink");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback originalCallback) {
        // the endpoint may want to know the exchange whose call is completing when the calling route continues
        final AsyncCallback callback = endpoint.completingCallback(exchange, originalCallback);
        try {
            final KameletConsumer consumer = getConsumer();
            if (consumer == null) {
                if (endpoint.isFailIfNoConsumers()) {
                    exchange.setException(new KameletConsumerNotAvailableException(noConsumerMessage(), exchange));
                } else {
                    LOG.debug("Exchange ignored, no consumers available on endpoint: {}", endpoint);
                }
                callback.done(true);
                return true;
            } else {
                // the kamelet producer has multiple purposes at this point
                // it is capable of linking the kamelet component with the kamelet EIP
                // to ensure the EIP and the component are wired together with their
                // kamelet:source and kamelet:sink endpoints so when calling the sink
                // then we continue processing the EIP child processors

                // if no EIP is in use, then its _just_ a regular camel component
                // with producer and consumers linked together via the component

                if (sink) {
                    // a source Kamelet gives the message its output data type when it hands it on, so the consuming
                    // route gets it (as an output type of the route, it would be applied only after the consuming route)
                    Processor outputType = sourceOutputType(exchange);
                    if (outputType != null) {
                        try {
                            outputType.process(exchange);
                        } catch (Exception e) {
                            exchange.setException(e);
                        }
                        if (exchange.getException() != null) {
                            callback.done(true);
                            return true;
                        }
                    }
                    // when calling a kamelet:sink then lookup any waiting processor
                    // from the Kamelet EIP to continue routing
                    AsyncProcessor eip = (AsyncProcessor) component.getKameletEip(key);
                    if (eip != null) {
                        return eip.process(exchange, callback);
                    } else {
                        // if the current route is from a kamelet source then we should
                        // break out as otherwise we would end up calling ourselves again
                        Route route = ExchangeHelper.getRoute(exchange);
                        boolean source = route != null && route.getConsumer() instanceof KameletConsumer;
                        if (source) {
                            callback.done(true);
                            return true;
                        }
                    }
                }
                if (registerKamelets) {
                    // kamelets are first-class registered as route (as old behavior)
                    return consumer.getAsyncProcessor().process(exchange, callback);
                } else {
                    // kamelet producer that calls its kamelet consumer to process the incoming exchange
                    // create exchange copy to let a new lifecycle originate from the calling route (not the kamelet route)
                    final Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false, true);
                    // fake copy as being created by the consumer
                    copy.getExchangeExtension().setFromEndpoint(consumer.getEndpoint());
                    copy.getExchangeExtension().setFromRouteId(consumer.getRouteId());
                    return consumer.getAsyncProcessor().process(copy, doneSync -> {
                        // copy result back after processing is done
                        ExchangeHelper.copyResults(exchange, copy);
                        callback.done(doneSync);
                    });
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.setException(e);
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    /**
     * The output data type of the source Kamelet whose route the exchange is in, from the route property that the
     * Kamelet YAML loader sets, or null when it has none. The outcome is looked up once and kept.
     */
    private Processor sourceOutputType(Exchange exchange) {
        if (!sourceOutputTypeResolved) {
            synchronized (this) {
                if (!sourceOutputTypeResolved) {
                    Route route = ExchangeHelper.getRoute(exchange);
                    Object type = route != null ? route.getProperties().get(Kamelet.SOURCE_OUTPUT_TYPE_PROPERTY) : null;
                    if (type != null) {
                        sourceOutputType = createSourceOutputType(route, type.toString());
                    }
                    sourceOutputTypeResolved = true;
                }
            }
        }
        return sourceOutputType;
    }

    private Processor createSourceOutputType(Route route, String type) {
        DataTypeProcessor processor = new DataTypeProcessor(null, type);
        processor.setCamelContext(getEndpoint().getCamelContext());
        try {
            ServiceHelper.startService(processor);
            return processor;
        } catch (Exception e) {
            // no transformer for the type: as the output type of the route did, convert a Java type with the type
            // converter, and take any other body as already of the type
            DataType dataType = new DataType(type);
            if (dataType.isJavaType() && dataType.getName() != null) {
                TypeConverterTransformer converter = new TypeConverterTransformer(dataType);
                return exchange -> converter.transform(exchange.getMessage(), DataType.ANY, dataType);
            }
            LOG.warn("The source Kamelet of route {} declares the output data type {}, but no transformer for it is on"
                     + " the classpath, so messages are handed on unchanged: {}",
                    route.getRouteId(), type, e.getMessage());
            return null;
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(sourceOutputType);
        sourceOutputType = null;
        sourceOutputTypeResolved = false;
        super.doStop();
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getKey() {
        return key;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ManagementStrategy ms = getEndpoint().getCamelContext().getManagementStrategy();
        if (ms != null && ms.getManagementAgent() != null) {
            registerKamelets = ms.getManagementAgent().getRegisterRoutesCreateByKamelet();
        }
    }

    /**
     * The message when there is no consumer: the text users and tools search for, then why there is none (the route of
     * the Kamelet and its status, or that there is no such route), how long the producer waited, and the options that
     * change this.
     */
    private String noConsumerMessage() {
        CamelContext context = endpoint.getCamelContext();
        String id = endpoint.getRouteId();
        StringBuilder sb = new StringBuilder(256);
        sb.append("No consumers available on endpoint: ").append(endpoint).append(". ");
        ServiceStatus status = context.getRoute(id) != null ? context.getRouteController().getRouteStatus(id) : null;
        if (status != null) {
            sb.append("Route ").append(id).append(" of Kamelet ").append(endpoint.getTemplateId())
                    .append(" in CamelContext ").append(context.getName()).append(" consumes from it and is ")
                    .append(status).append(". ");
        } else {
            sb.append("CamelContext ").append(context.getName()).append(" has no route ").append(id)
                    .append(" of Kamelet ").append(endpoint.getTemplateId()).append(". ");
        }
        if (block && timeout > 0) {
            sb.append("The producer waited ").append(timeout).append(" millis for a consumer");
        } else {
            sb.append("The producer did not wait for a consumer");
        }
        sb.append(" (see the block, timeout and failIfNoConsumers options)");
        return sb.toString();
    }

    /**
     * Gets the consumer, which is looked up again when it has been added or removed (such as when its route is
     * suspended or stopped) since it was looked up last.
     */
    private KameletConsumer getConsumer() throws InterruptedException {
        CachedConsumer cached = cachedConsumer;
        // read the counter before the lookup, so a change during the lookup makes the next exchange look up again
        int stateCounter = component.getStateCounter();
        if (cached == null || cached.consumer() == null || cached.stateCounter() != stateCounter) {
            KameletConsumer consumer = component.getConsumer(key, block, timeout);
            cachedConsumer = new CachedConsumer(consumer, stateCounter);
            return consumer;
        }
        return cached.consumer();
    }

    private record CachedConsumer(KameletConsumer consumer, int stateCounter) {
    }
}
