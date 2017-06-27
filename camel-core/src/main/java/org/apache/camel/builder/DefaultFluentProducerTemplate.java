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
package org.apache.camel.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.processor.ConvertBodyProcessor;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

public class DefaultFluentProducerTemplate extends ServiceSupport implements FluentProducerTemplate {
    private final CamelContext context;
    private final ClassValue<ConvertBodyProcessor> resultProcessors;
    private Map<String, Object> headers;
    private Object body;
    private Optional<Consumer<ProducerTemplate>> templateCustomizer;
    private Optional<Supplier<Exchange>> exchangeSupplier;
    private Optional<Supplier<Processor>> processorSupplier;
    private Optional<Endpoint> endpoint;
    private Optional<Endpoint> defaultEndpoint;
    private int maximumCacheSize;
    private boolean eventNotifierEnabled;
    private volatile ProducerTemplate template;

    public DefaultFluentProducerTemplate(CamelContext context) {
        this.context = context;
        this.endpoint = Optional.empty();
        this.defaultEndpoint = Optional.empty();
        this.eventNotifierEnabled = true;
        this.templateCustomizer = Optional.empty();
        this.exchangeSupplier = Optional.empty();
        this.processorSupplier = Optional.empty();
        this.resultProcessors = new ClassValue<ConvertBodyProcessor>() {
            @Override
            protected ConvertBodyProcessor computeValue(Class<?> type) {
                return new ConvertBodyProcessor(type);
            }
        };
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
        return defaultEndpoint.orElse(null);
    }

    @Override
    public void setDefaultEndpoint(Endpoint defaultEndpoint) {
        this.defaultEndpoint = Optional.ofNullable(defaultEndpoint);
    }

    @Override
    public int getMaximumCacheSize() {
        return maximumCacheSize;
    }

    @Override
    public void setMaximumCacheSize(int maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }

    @Override
    public boolean isEventNotifierEnabled() {
        return eventNotifierEnabled;
    }

    @Override
    public void setEventNotifierEnabled(boolean eventNotifierEnabled) {
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
        if (headers == null) {
            headers = new HashMap<>();
        }

        headers.put(key, value);

        return this;
    }

    @Override
    public FluentProducerTemplate clearHeaders() {
        if (headers != null) {
            headers.clear();
        }

        return this;
    }

    @Override
    public FluentProducerTemplate withBody(Object body) {
        this.body = body;

        return this;
    }

    @Override
    public FluentProducerTemplate withBodyAs(Object body, Class<?> type) {
        this.body = type != null
            ? context.getTypeConverter().convertTo(type, body)
            : body;

        return this;
    }

    @Override
    public FluentProducerTemplate clearBody() {
        this.body = null;

        return this;
    }

    @Override
    public FluentProducerTemplate withTemplateCustomizer(final Consumer<ProducerTemplate> templateCustomizer) {
        this.templateCustomizer = Optional.of(templateCustomizer);
        return this;
    }

    @Override
    public FluentProducerTemplate withExchange(final Exchange exchange) {
        return withExchange(() -> exchange);
    }

    @Override
    public FluentProducerTemplate withExchange(final Supplier<Exchange> exchangeSupplier) {
        this.exchangeSupplier = Optional.of(exchangeSupplier);
        return this;
    }

    @Override
    public FluentProducerTemplate withProcessor(final Processor processor) {
        return withProcessor(() -> processor);
    }

    @Override
    public FluentProducerTemplate withProcessor(final Supplier<Processor> processorSupplier) {
        this.processorSupplier = Optional.of(processorSupplier);
        return this;
    }

    @Override
    public FluentProducerTemplate to(String endpointUri) {
        return to(context.getEndpoint(endpointUri));
    }

    @Override
    public FluentProducerTemplate to(Endpoint endpoint) {
        this.endpoint = Optional.of(endpoint);
        return this;
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
        // Determine the target endpoint
        final Endpoint target = target();

        // Create the default processor if not provided.
        final Supplier<Processor> processorSupplier = this.processorSupplier.orElse(() -> defaultProcessor());

        T result;
        if (type == Exchange.class) {
            result = (T)template().request(target, processorSupplier.get());
        } else if (type == Message.class) {
            Exchange exchange = template().request(target, processorSupplier.get());
            result = exchange.hasOut() ? (T)exchange.getOut() : (T)exchange.getIn();
        } else {
            Exchange exchange = template().send(
                target,
                ExchangePattern.InOut,
                processorSupplier.get(),
                resultProcessors.get(type)
            );

            result = context.getTypeConverter().convertTo(
                type,
                ExchangeHelper.extractResultBody(exchange, exchange.getPattern())
            );
        }

        return result;
    }

    @Override
    public Future<Object> asyncRequest() {
        return asyncRequest(Object.class);
    }

    @Override
    public <T> Future<T> asyncRequest(Class<T> type) {
        // Determine the target endpoint
        final Endpoint target = target();

        Future<T> result;
        if (ObjectHelper.isNotEmpty(headers)) {
            // Make a copy of the headers and body so that async processing won't
            // be invalidated by subsequent reuse of the template
            final Map<String, Object> headersCopy = new HashMap<>(headers);
            final Object bodyCopy = body;

            result = template().asyncRequestBodyAndHeaders(target, bodyCopy, headersCopy, type);
        } else {
            // Make a copy of the and body so that async processing won't be
            // invalidated by subsequent reuse of the template
            final Object bodyCopy = body;

            result = template().asyncRequestBody(target, bodyCopy, type);
        }

        return result;
    }

    // ************************
    // SEND
    // ************************

    @Override
    public Exchange send() throws CamelExecutionException {
        // Determine the target endpoint
        final Endpoint target = target();

        return exchangeSupplier.isPresent()
            ? template().send(target, exchangeSupplier.get().get())
            : template().send(target, processorSupplier.orElse(() -> defaultProcessor()).get());
    }

    @Override
    public Future<Exchange> asyncSend() {
        // Determine the target endpoint
        final Endpoint target = target();

        return exchangeSupplier.isPresent()
            ? template().asyncSend(target, exchangeSupplier.get().get())
            : template().asyncSend(target, processorSupplier.orElse(() -> defaultAsyncProcessor()).get());
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
        return new DefaultFluentProducerTemplate(context);
    }

    private ProducerTemplate template() {
        ObjectHelper.notNull(context, "CamelContext");

        if (template == null) {
            template = maximumCacheSize > 0 ? context.createProducerTemplate(maximumCacheSize) : context.createProducerTemplate();
            defaultEndpoint.ifPresent(template::setDefaultEndpoint);
            template.setEventNotifierEnabled(eventNotifierEnabled);
            templateCustomizer.ifPresent(tc -> tc.accept(template));
        }

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
        if (endpoint.isPresent()) {
            return endpoint.get();
        }
        if (defaultEndpoint.isPresent()) {
            return defaultEndpoint.get();
        }

        throw new IllegalArgumentException("No endpoint configured on FluentProducerTemplate. You can configure an endpoint with to(uri)");
    }

    @Override
    protected void doStart() throws Exception {
        if (template == null) {
            template = template();
        }
        ServiceHelper.startService(template);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(template);
    }
}
