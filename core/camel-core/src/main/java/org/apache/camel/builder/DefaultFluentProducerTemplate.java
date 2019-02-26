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
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class DefaultFluentProducerTemplate extends ServiceSupport implements FluentProducerTemplate {

    // transient state of headers and body which needs to be thread local scoped to be thread-safe
    private final ThreadLocal<Map<String, Object>> headers = new ThreadLocal<>();
    private final ThreadLocal<Object> body = new ThreadLocal<>();

    private final CamelContext context;
    private final ClassValue<ConvertBodyProcessor> resultProcessors;
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
        Map<String, Object> map = headers.get();
        if (map == null) {
            map = new HashMap<>();
            headers.set(map);
        }

        map.put(key, value);

        return this;
    }

    @Override
    public FluentProducerTemplate clearHeaders() {
        headers.remove();

        return this;
    }

    @Override
    public FluentProducerTemplate withBody(Object body) {
        this.body.set(body);

        return this;
    }

    @Override
    public FluentProducerTemplate withBodyAs(Object body, Class<?> type) {
        Object b = type != null
            ? context.getTypeConverter().convertTo(type, body)
            : body;

        this.body.set(b);

        return this;
    }

    @Override
    public FluentProducerTemplate clearBody() {
        body.remove();

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
        if (exchangeSupplier.isPresent()) {
            throw new IllegalArgumentException("withExchange not supported on FluentProducerTemplate.request method. Use send method instead.");
        }

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
        if (ObjectHelper.isNotEmpty(headers.get())) {
            // Make a copy of the headers and body so that async processing won't
            // be invalidated by subsequent reuse of the template
            final Map<String, Object> headersCopy = new HashMap<>(headers.get());
            final Object bodyCopy = body.get();

            result = template().asyncRequestBodyAndHeaders(target, bodyCopy, headersCopy, type);
        } else {
            // Make a copy of the and body so that async processing won't be
            // invalidated by subsequent reuse of the template
            final Object bodyCopy = body.get();

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
            template = context.createProducerTemplate(maximumCacheSize);
            defaultEndpoint.ifPresent(template::setDefaultEndpoint);
            template.setEventNotifierEnabled(eventNotifierEnabled);
            templateCustomizer.ifPresent(tc -> tc.accept(template));
        }

        return template;
    }

    private Processor defaultProcessor() {
        return exchange -> {
            ObjectHelper.ifNotEmpty(headers.get(), exchange.getIn().getHeaders()::putAll);
            ObjectHelper.ifNotEmpty(body.get(), exchange.getIn()::setBody);
        };
    }

    private Processor defaultAsyncProcessor() {
        final Map<String, Object> headersCopy = ObjectHelper.isNotEmpty(this.headers.get()) ? new HashMap<>(this.headers.get()) : null;
        final Object bodyCopy = this.body.get();

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
        clearAll();
        ServiceHelper.stopService(template);
    }
}
