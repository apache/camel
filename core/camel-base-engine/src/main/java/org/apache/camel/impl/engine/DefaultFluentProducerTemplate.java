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
package org.apache.camel.impl.engine;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.ConvertBodyProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class DefaultFluentProducerTemplate extends ServiceSupport implements FluentProducerTemplate {

    // transient state of endpoint, headers and body which needs to be thread local scoped to be thread-safe
    private Map<String, Object> headers;
    private Object body;
    private Endpoint endpoint;
    private Supplier<Exchange> exchangeSupplier;
    private Supplier<Processor> processorSupplier;
    private Consumer<ProducerTemplate> templateCustomizer;

    private final CamelContext context;
    private final ProcessorFactory processorFactory;
    private final ClassValue<Processor> resultProcessors;
    private Endpoint defaultEndpoint;
    private int maximumCacheSize;
    private boolean eventNotifierEnabled;
    private volatile ProducerTemplate template;
    private volatile boolean cloned;

    public DefaultFluentProducerTemplate(CamelContext context) {
        this.context = context;
        this.processorFactory = context.adapt(ExtendedCamelContext.class).getProcessorFactory();
        this.eventNotifierEnabled = true;
        this.resultProcessors = new ClassValue<Processor>() {
            @Override
            protected Processor computeValue(Class<?> type) {
                return new ConvertBodyProcessor(type);
            }
        };
    }

    private DefaultFluentProducerTemplate(CamelContext context, ClassValue<Processor> resultProcessors,
                                          Endpoint defaultEndpoint, int maximumCacheSize, boolean eventNotifierEnabled,
                                          ProducerTemplate template) {
        this.context = context;
        this.processorFactory = context.adapt(ExtendedCamelContext.class).getProcessorFactory();
        this.resultProcessors = resultProcessors;
        this.defaultEndpoint = defaultEndpoint;
        this.maximumCacheSize = maximumCacheSize;
        this.eventNotifierEnabled = eventNotifierEnabled;
        this.template = template;
        this.cloned = true;
    }

    private DefaultFluentProducerTemplate newClone() {
        return new DefaultFluentProducerTemplate(
                context, resultProcessors, defaultEndpoint, maximumCacheSize, eventNotifierEnabled, template);
    }

    private DefaultFluentProducerTemplate checkCloned() {
        if (!cloned) {
            return newClone();
        } else {
            return this;
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public int getCurrentCacheSize() {
        if (template == null) {
            return 0;
        }
        return template.getCurrentCacheSize();
    }

    @Override
    public void cleanUp() {
        if (template != null) {
            template.cleanUp();
        }
    }

    @Override
    public void setDefaultEndpointUri(String endpointUri) {
        setDefaultEndpoint(getCamelContext().getEndpoint(endpointUri));
    }

    @Override
    public Endpoint getDefaultEndpoint() {
        return defaultEndpoint;
    }

    @Override
    public void setDefaultEndpoint(Endpoint defaultEndpoint) {
        if (this.defaultEndpoint != null && isStarted()) {
            throw new IllegalArgumentException("Not allowed after template has been started");
        }
        this.defaultEndpoint = defaultEndpoint;
    }

    @Override
    public int getMaximumCacheSize() {
        return maximumCacheSize;
    }

    @Override
    public void setMaximumCacheSize(int maximumCacheSize) {
        if (this.maximumCacheSize != 0 && isStarted()) {
            throw new IllegalArgumentException("Not allowed after template has been started");
        }
        this.maximumCacheSize = maximumCacheSize;
    }

    @Override
    public boolean isEventNotifierEnabled() {
        return eventNotifierEnabled;
    }

    @Override
    public void setEventNotifierEnabled(boolean eventNotifierEnabled) {
        if (isStarted()) {
            throw new IllegalArgumentException("Not allowed after template has been started");
        }
        this.eventNotifierEnabled = eventNotifierEnabled;
    }

    @Override
    public FluentProducerTemplate clearAll() {
        clearBody();
        clearHeaders();

        return this;
    }

    @Override
    public FluentProducerTemplate withHeader(String key, Object value) {
        DefaultFluentProducerTemplate clone = checkCloned();

        Map<String, Object> map = clone.headers;
        if (map == null) {
            map = new LinkedHashMap<>();
            clone.headers = map;
        }
        map.put(key, value);
        return clone;
    }

    @Override
    public FluentProducerTemplate clearHeaders() {
        DefaultFluentProducerTemplate clone = checkCloned();

        if (clone.headers != null) {
            clone.headers.clear();
        }
        return clone;
    }

    @Override
    public FluentProducerTemplate withBody(Object body) {
        DefaultFluentProducerTemplate clone = checkCloned();

        clone.body = body;
        return clone;
    }

    @Override
    public FluentProducerTemplate withBodyAs(Object body, Class<?> type) {
        DefaultFluentProducerTemplate clone = checkCloned();

        Object b = type != null
                ? clone.context.getTypeConverter().convertTo(type, body)
                : body;
        clone.body = b;
        return clone;
    }

    @Override
    public FluentProducerTemplate clearBody() {
        DefaultFluentProducerTemplate clone = checkCloned();

        clone.body = null;
        return clone;
    }

    @Override
    public FluentProducerTemplate withTemplateCustomizer(final Consumer<ProducerTemplate> templateCustomizer) {
        if (this.templateCustomizer != null && isStarted()) {
            throw new IllegalArgumentException("Not allowed after template has been started");
        }
        this.templateCustomizer = templateCustomizer;

        if (template != null) {
            // need to re-initialize template since we have a customizer
            ServiceHelper.stopService(template);
            templateCustomizer.accept(template);
            ServiceHelper.startService(template);
        }

        return this;
    }

    @Override
    public FluentProducerTemplate withExchange(final Exchange exchange) {
        return withExchange(() -> exchange);
    }

    @Override
    public FluentProducerTemplate withExchange(final Supplier<Exchange> exchangeSupplier) {
        DefaultFluentProducerTemplate clone = checkCloned();

        clone.exchangeSupplier = exchangeSupplier;
        return clone;
    }

    @Override
    public FluentProducerTemplate withProcessor(final Processor processor) {
        return withProcessor(() -> processor);
    }

    @Override
    public FluentProducerTemplate withProcessor(final Supplier<Processor> processorSupplier) {
        DefaultFluentProducerTemplate clone = checkCloned();

        clone.processorSupplier = processorSupplier;
        return clone;
    }

    @Override
    public FluentProducerTemplate to(String endpointUri) {
        return to(context.getEndpoint(endpointUri));
    }

    @Override
    public FluentProducerTemplate to(Endpoint endpoint) {
        DefaultFluentProducerTemplate clone = checkCloned();

        clone.endpoint = endpoint;
        return clone;
    }

    // ************************
    // REQUEST
    // ************************

    @Override
    public Object request() throws CamelExecutionException {
        return request(Object.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T request(Class<T> type) throws CamelExecutionException {
        if (exchangeSupplier != null && exchangeSupplier.get() != null) {
            throw new IllegalArgumentException(
                    "withExchange not supported on FluentProducerTemplate.request method. Use send method instead.");
        }

        DefaultFluentProducerTemplate clone = checkCloned();

        // Determine the target endpoint
        final Endpoint target = clone.target();

        // Create the default processor if not provided.
        Processor processor = clone.processorSupplier != null ? clone.processorSupplier.get() : null;
        final Processor processorSupplier = processor != null ? processor : clone.defaultProcessor();

        // reset cloned flag so when we use it again it has to set values again
        cloned = false;

        T result;
        if (type == Exchange.class) {
            result = (T) clone.template().request(target, processorSupplier);
        } else if (type == Message.class) {
            Exchange exchange = clone.template().request(target, processorSupplier);
            result = (T) exchange.getMessage();
        } else {
            Exchange exchange = clone.template().send(
                    target,
                    ExchangePattern.InOut,
                    processorSupplier,
                    clone.resultProcessors.get(type));

            result = clone.context.getTypeConverter().convertTo(
                    type,
                    ExchangeHelper.extractResultBody(exchange, exchange.getPattern()));
        }

        return result;
    }

    @Override
    public Future<Object> asyncRequest() {
        return asyncRequest(Object.class);
    }

    @Override
    public <T> Future<T> asyncRequest(Class<T> type) {
        DefaultFluentProducerTemplate clone = checkCloned();

        // Determine the target endpoint
        final Endpoint target = clone.target();

        // reset cloned flag so when we use it again it has to set values again
        cloned = false;

        Future<T> result;
        if (ObjectHelper.isNotEmpty(clone.headers)) {
            // Make a copy of the headers and body so that async processing won't
            // be invalidated by subsequent reuse of the template
            final Map<String, Object> headersCopy = new HashMap<>(clone.headers);
            final Object bodyCopy = clone.body;

            result = clone.template().asyncRequestBodyAndHeaders(target, bodyCopy, headersCopy, type);
        } else {
            // Make a copy of the and body so that async processing won't be
            // invalidated by subsequent reuse of the template
            final Object bodyCopy = clone.body;

            result = clone.template().asyncRequestBody(target, bodyCopy, type);
        }

        return result;
    }

    // ************************
    // SEND
    // ************************

    @Override
    public Exchange send() throws CamelExecutionException {
        DefaultFluentProducerTemplate clone = checkCloned();

        // Determine the target endpoint
        final Endpoint target = clone.target();

        // reset cloned flag so when we use it again it has to set values again
        cloned = false;

        Exchange exchange = clone.exchangeSupplier != null ? clone.exchangeSupplier.get() : null;
        if (exchange != null) {
            return clone.template().send(target, exchange);
        } else {
            Processor proc = clone.processorSupplier != null ? clone.processorSupplier.get() : null;
            final Processor processor = proc != null ? proc : clone.defaultProcessor();
            return clone.template().send(target, processor);
        }
    }

    @Override
    public Future<Exchange> asyncSend() {
        DefaultFluentProducerTemplate clone = checkCloned();

        // Determine the target endpoint
        final Endpoint target = clone.target();

        // reset cloned flag so when we use it again it has to set values again
        cloned = false;

        Exchange exchange = clone.exchangeSupplier != null ? clone.exchangeSupplier.get() : null;
        if (exchange != null) {
            return clone.template().asyncSend(target, exchange);
        } else {
            Processor proc = clone.processorSupplier != null ? clone.processorSupplier.get() : null;
            final Processor processor = proc != null ? proc : clone.defaultAsyncProcessor();
            return clone.template().asyncSend(target, processor);
        }
    }

    // ************************
    // HELPERS
    // ************************

    /**
     * Create the FluentProducerTemplate by setting the camel context
     *
     * @param context the camel context
     */
    public static FluentProducerTemplate on(CamelContext context) {
        DefaultFluentProducerTemplate fluent = new DefaultFluentProducerTemplate(context);
        fluent.start();
        // mark it as cloned as its started
        fluent.cloned = true;
        return fluent;
    }

    private ProducerTemplate template() {
        return template;
    }

    private Processor defaultProcessor() {
        return exchange -> {
            ObjectHelper.ifNotEmpty(headers, exchange.getIn().getHeaders()::putAll);
            ObjectHelper.ifNotEmpty(body, exchange.getIn()::setBody);
        };
    }

    private Processor defaultAsyncProcessor() {
        final Map<String, Object> headersCopy = ObjectHelper.isNotEmpty(this.headers) ? new HashMap<>(this.headers) : null;
        final Object bodyCopy = this.body;

        return exchange -> {
            ObjectHelper.ifNotEmpty(headersCopy, exchange.getIn().getHeaders()::putAll);
            ObjectHelper.ifNotEmpty(bodyCopy, exchange.getIn()::setBody);
        };
    }

    private Endpoint target() {
        if (endpoint != null) {
            return endpoint;
        }
        if (defaultEndpoint != null) {
            return defaultEndpoint;
        }

        if (template != null && template.getDefaultEndpoint() != null) {
            return template.getDefaultEndpoint();
        }

        throw new IllegalArgumentException(
                "No endpoint configured on FluentProducerTemplate. You can configure an endpoint with to(uri)");
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(context, "CamelContext");
        template = context.createProducerTemplate(maximumCacheSize);
        if (defaultEndpoint != null) {
            template.setDefaultEndpoint(defaultEndpoint);
        }
        template.setEventNotifierEnabled(eventNotifierEnabled);
        if (templateCustomizer != null) {
            templateCustomizer.accept(template);
        }
        ServiceHelper.initService(template);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(template);
    }

    @Override
    protected void doStop() throws Exception {
        clearAll();
        this.endpoint = null;
        this.exchangeSupplier = null;
        this.processorSupplier = null;
        this.templateCustomizer = null;
        ServiceHelper.stopService(template);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(template);
        template = null;
    }
}
