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
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

public class FluentProducerTemplate {
    private final CamelContext context;
    private Map<String, Object> headers;
    private Object body;
    private Endpoint endpoint;
    private Consumer<ProducerTemplate> templateCustomizer;
    private Supplier<Exchange> exchangeSupplier;
    private Supplier<Processor> processorSupplier;
    private ProducerTemplate template;

    public FluentProducerTemplate(CamelContext context) {
        this.context = context;
        this.headers = null;
        this.body = null;
        this.endpoint = null;
        this.templateCustomizer = null;
        this.exchangeSupplier = null;
        this.processorSupplier = () -> this::populateExchange;
        this.template = null;
    }

    /**
     * Set the header
     *
     * @param key the key of the header
     * @param value the value of the header
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate withHeader(String key, Object value) {
        if (headers == null) {
            headers = new HashMap<>();
        }

        headers.put(key, value);

        return this;
    }

    /**
     * Remove the headers.
     *
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate clearHeaders() {
        if (headers != null) {
            headers.clear();
        }

        return this;
    }

    /**
     * Set the message body
     *
     * @param body the body
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate withBody(Object body) {
        this.body = body;

        return this;
    }

    /**
     * Set the message body after converting it to the given type
     *
     * @param body the body
     * @param type the type which the body should be converted to
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate withBodyAs(Object body, Class<?> type) {
        this.body = type != null
            ? context.getTypeConverter().convertTo(type, body)
            : body;

        return this;
    }

    /**
     * Remove the body.
     *
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate clearBody() {
        this.body = null;

        return this;
    }

    /**
     * To customize the producer template for advanced usage like to set the
     * executor service to use.
     *
     * <pre>
     * {@code
     * FluentProducerTemplate.on(context)
     *     .withTemplateCustomizer(
     *         template -> {
     *             template.setExecutorService(myExecutor);
     *             template.setMaximumCacheSize(10);
     *         }
     *      )
     *     .withBody("the body")
     *     .to("direct:start")
     *     .request()
     * </pre>
     *
     * Note that it is invoked only once.
     *
     * @param templateCustomizer
     * @return
     */
    public FluentProducerTemplate withTemplateCustomizer(final Consumer<ProducerTemplate> templateCustomizer) {
        this.templateCustomizer = templateCustomizer;
        return this;
    }

    /**
     * Set the exchange to use for send.
     *
     * @param exchange
     * @return
     */
    public FluentProducerTemplate withExchange(final Exchange exchange) {
        return withExchange(() -> exchange);
    }

    /**
     * Set the exchangeSupplier which will be invoke to get the exchange to be
     * used for send.
     *
     * @param exchangeSupplier
     * @return
     */
    public FluentProducerTemplate withExchange(final Supplier<Exchange> exchangeSupplier) {
        this.exchangeSupplier = exchangeSupplier;
        return this;
    }

    /**
     * Set the processor to use for send/request.
     *
     * <pre>
     * {@code
     * FluentProducerTemplate.on(context)
     *     .withProcessor(
     *         exchange -> {
     *             exchange.getIn().setHeader("Key1", "Val1")
     *             exchange.getIn().setHeader("Key2", "Val2")
     *             exchange.getIn().setBody("the body")
     *         }
     *      )
     *     .to("direct:start")
     *     .request()
     * </pre>
     *
     * @param processor
     * @return
     */
    public FluentProducerTemplate withProcessor(final Processor processor) {
        return withProcessor(() -> processor);
    }

    /**
     * Set the processorSupplier which will be invoke to get the processor to be
     * used for send/request.
     *
     * @param processorSupplier
     * @return
     */
    public FluentProducerTemplate withProcessor(final Supplier<Processor> processorSupplier) {
        this.processorSupplier = processorSupplier;
        return this;
    }

    /**
     * Set the message body
     *
     * @param endpointUri the endpoint URI to send to
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate to(String endpointUri) {
        return to(context.getEndpoint(endpointUri));
    }

    /**
     * Set the message body
     *
     * @param endpoint the endpoint to send to
     * @return this FluentProducerTemplate instance
     */
    public FluentProducerTemplate to(Endpoint endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    // ************************
    // REQUEST
    // ************************

    /**
     * Send to an endpoint returning any result output body.
     *
     * @return the result
     * @throws CamelExecutionException
     */
    public Object request() throws CamelExecutionException {
        return request(Object.class);
    }

    /**
     * Send to an endpoint.
     *
     * @param type the expected response type
     * @return the result
     * @throws CamelExecutionException
     */
    @SuppressWarnings("unchecked")
    public <T> T request(Class<T> type) throws CamelExecutionException {
        T result;
        if (type == Exchange.class) {
            result = (T)template().request(endpoint, processorSupplier.get());
        } else if (type == Message.class) {
            Exchange exchange = template().request(endpoint, processorSupplier.get());
            result = exchange.hasOut() ? (T)exchange.getOut() : (T)exchange.getIn();
        } else {
            Exchange exchange = template().send(endpoint, ExchangePattern.InOut, processorSupplier.get());
            result = context.getTypeConverter().convertTo(
                type,
                ExchangeHelper.extractResultBody(exchange, exchange.getPattern())
            );
        }

        return result;
    }

    /**
     * Sends asynchronously to the given endpoint.
     *
     * @return a handle to be used to get the response in the future
     */
    public Future<Object> asyncRequest() {
        return asyncRequest(Object.class);
    }

    /**
     * Sends asynchronously to the given endpoint.
     *
     * @param type the expected response type
     * @return a handle to be used to get the response in the future
     */
    public <T> Future<T> asyncRequest(Class<T> type) {
        Future<T> result;
        if (headers != null) {
            result = template().asyncRequestBodyAndHeaders(endpoint, body, headers, type);
        } else {
            result = template().asyncRequestBody(endpoint, body, type);
        }

        return result;
    }

    // ************************
    // SEND
    // ************************

    /**
     * Send to an endpoint
     *
     * @throws CamelExecutionException
     */
    public Exchange send() throws CamelExecutionException {
        return exchangeSupplier != null
            ? template().send(endpoint, exchangeSupplier.get())
            : template().send(endpoint, processorSupplier.get());
    }

    /**
     * Sends asynchronously to the given endpoint.
     *
     * @return a handle to be used to get the response in the future
     */
    public Future<Exchange> asyncSend() {
        return exchangeSupplier != null
            ? template().asyncSend(endpoint, exchangeSupplier.get())
            : template().asyncSend(endpoint, processorSupplier.get());
    }

    // ************************
    // HELPERS
    // ************************

    /**
     * Create the FluentProducerTemplate by setting the camel context
     *
     * @param context the camel context
     * @return this FluentProducerTemplate instance
     */
    public static FluentProducerTemplate on(CamelContext context) {
        return new FluentProducerTemplate(context);
    }

    private ProducerTemplate template() {
        ObjectHelper.notNull(context, "camel-context");
        ObjectHelper.notNull(endpoint, "endpoint");

        if (this.template == null) {
            template = context.createProducerTemplate();
            if (templateCustomizer != null) {
                templateCustomizer.accept(template);
            }
        }

        return template;
    }

    private void populateExchange(Exchange exchange) throws Exception {
        if (headers != null && !headers.isEmpty()) {
            exchange.getIn().getHeaders().putAll(headers);
        }
        if (body != null) {
            exchange.getIn().setBody(body);
        }
    }
}
