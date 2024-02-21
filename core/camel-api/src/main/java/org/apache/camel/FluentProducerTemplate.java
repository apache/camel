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
package org.apache.camel;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.camel.util.ObjectHelper;

/**
 * Template for working with Camel and sending {@link Message} instances in an {@link Exchange} to an {@link Endpoint}
 * using a <i>fluent</i> build style. <br/>
 * <p/>
 * <b>Important:</b> Read the javadoc of each method carefully to ensure the behavior of the method is understood. Some
 * methods is for <tt>InOnly</tt>, others for <tt>InOut</tt> MEP. And some methods throws
 * {@link org.apache.camel.CamelExecutionException} while others stores any thrown exception on the returned
 * {@link Exchange}. <br/>
 * <p/>
 * The {@link FluentProducerTemplate} is <b>thread safe</b> with the assumption that its the same (single) thread that
 * builds the message (via the fluent methods) that also sends the message. <br/>
 * <p/>
 * When using the fluent template its required to chain the methods such as:
 *
 * <pre>
 *     FluentProducerTemplate fluent = ...
 *     fluent.withHeader("foo", 123).withHeader("bar", 456).withBody("Hello World").to("kafka:cheese").send();
 * </pre>
 *
 * The following code is <b>wrong</b> (do not do this)
 *
 * <pre>
 *     FluentProducerTemplate fluent = ...
 *     fluent.withHeader("foo", 123);
 *     fluent.withHeader("bar", 456);
 *     fluent.withBody("Hello World");
 *     fluent.to("kafka:cheese");
 *     fluent.send();
 * </pre>
 *
 * If you do not want to chain fluent methods you can do as follows:
 *
 * <pre>
 *     FluentProducerTemplate fluent = ...
 *     fluent = fluent.withHeader("foo", 123);
 *     fluent = fluent.withHeader("bar", 456);
 *     fluent = fluent.withBody("Hello World");
 *     fluent = fluent.to("kafka:cheese")
 *     fluent.send();
 * </pre>
 * <p/>
 * You can either only use either withExchange, or withProcessor or a combination of withBody/withHeaders to construct
 * the message to be sent.<br/>
 * <p/>
 * All the methods which sends a message may throw {@link FailedToCreateProducerException} in case the {@link Producer}
 * could not be created. Or a {@link NoSuchEndpointException} if the endpoint could not be resolved. There may be other
 * related exceptions being thrown which occurs <i>before</i> the {@link Producer} has started sending the message.<br/>
 * <p/>
 * All the send or request methods will return the content according to this strategy:
 * <ul>
 * <li>throws {@link org.apache.camel.CamelExecutionException} if processing failed <i>during</i> routing with the
 * caused exception wrapped</li>
 * <li>The <tt>fault.body</tt> if there is a fault message set and its not <tt>null</tt></li>
 * <li>Either <tt>IN</tt> or <tt>OUT</tt> body according to the message exchange pattern. If the pattern is Out capable
 * then the <tt>OUT</tt> body is returned, otherwise <tt>IN</tt>.
 * </ul>
 * <br/>
 * <p/>
 * Before using the template it must be started. And when you are done using the template, make sure to {@link #stop()}
 * the template.<br/>
 * <p/>
 * <b>Important note on usage:</b> See this
 * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">FAQ entry</a> before
 * using.
 *
 * @see ProducerTemplate
 * @see ConsumerTemplate
 */
public interface FluentProducerTemplate extends Service {

    /**
     * Get the {@link CamelContext}
     *
     * @return camelContext the Camel context
     */
    CamelContext getCamelContext();

    // Configuration methods
    // -----------------------------------------------------------------------

    /**
     * Gets the maximum cache size used in the backing cache pools.
     *
     * @return the maximum cache size
     */
    int getMaximumCacheSize();

    /**
     * Sets a custom maximum cache size to use in the backing cache pools.
     *
     * @param maximumCacheSize the custom maximum cache size
     */
    void setMaximumCacheSize(int maximumCacheSize);

    /**
     * Gets an approximated size of the current cached resources in the backing cache pools.
     *
     * @return the size of current cached resources
     */
    int getCurrentCacheSize();

    /**
     * Get the default endpoint to use if none is specified
     *
     * @return the default endpoint instance
     */
    Endpoint getDefaultEndpoint();

    /**
     * Sets the default endpoint to use if none is specified
     *
     * @param defaultEndpoint the default endpoint instance
     */
    void setDefaultEndpoint(Endpoint defaultEndpoint);

    /**
     * Sets the default endpoint uri to use if none is specified
     *
     * @param endpointUri the default endpoint uri
     */
    void setDefaultEndpointUri(String endpointUri);

    /**
     * Sets whether the {@link org.apache.camel.spi.EventNotifier} should be used by this {@link ProducerTemplate} to
     * send events about the {@link Exchange} being sent.
     * <p/>
     * By default this is enabled.
     *
     * @param enabled <tt>true</tt> to enable, <tt>false</tt> to disable.
     */
    void setEventNotifierEnabled(boolean enabled);

    /**
     * Whether the {@link org.apache.camel.spi.EventNotifier} should be used by this {@link ProducerTemplate} to send
     * events about the {@link Exchange} being sent.
     *
     * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise
     */
    boolean isEventNotifierEnabled();

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

    // Fluent methods
    // -----------------------------------------------------------------------

    /**
     * Set the headers
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param headers the headers
     */
    FluentProducerTemplate withHeaders(Map<String, Object> headers);

    /**
     * Set the header
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param key   the key of the header
     * @param value the value of the header
     */
    FluentProducerTemplate withHeader(String key, Object value);

    /**
     * Set the exchange properties
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param properties the exchange properties
     */
    FluentProducerTemplate withExchangeProperties(Map<String, Object> properties);

    /**
     * Set the exchange property
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param key   the key of the exchange property
     * @param value the value of the exchange property
     */
    FluentProducerTemplate withExchangeProperty(String key, Object value);

    /**
     * Set the variables
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param variables the variables
     */
    FluentProducerTemplate withVariables(Map<String, Object> variables);

    /**
     * Set the exchange property
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param key   the key of the variable
     * @param value the value of the variable
     */
    FluentProducerTemplate withVariable(String key, Object value);

    /**
     * Set the message body
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param body the body
     */
    FluentProducerTemplate withBody(Object body);

    /**
     * Set the message body after converting it to the given type
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param body the body
     * @param type the type which the body should be converted to
     */
    FluentProducerTemplate withBodyAs(Object body, Class<?> type);

    /**
     * To customize the producer template for advanced usage like to set the executor service to use.
     *
     * <pre>
     * {@code
     * FluentProducerTemplate fluent = context.createFluentProducerTemplate();
     * fluent.withTemplateCustomizer(
     *         t -> {
     *             t.setExecutorService(myExecutor);
     *             t.setMaximumCacheSize(10);
     *         }
     *      )
     *     .withBody("the body")
     *     .to("direct:start")
     *     .send()}
     * </pre>
     *
     * Note that it is invoked only once.
     *
     * @param templateCustomizer the customizer
     */
    FluentProducerTemplate withTemplateCustomizer(java.util.function.Consumer<ProducerTemplate> templateCustomizer);

    /**
     * Set the exchange to use for send.
     *
     * When using withExchange then you must use the send method (request is not supported).
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param exchange the exchange
     */
    FluentProducerTemplate withExchange(Exchange exchange);

    /**
     * Set the exchangeSupplier which will be invoke to get the exchange to be used for send.
     *
     * When using withExchange then you must use the send method (request is not supported).
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param exchangeSupplier the supplier
     */
    FluentProducerTemplate withExchange(Supplier<Exchange> exchangeSupplier);

    /**
     * Set the processor to use for send/request.
     *
     * <pre>
     * {@code
     * FluentProducerTemplate.on(context)
     *         .withProcessor(
     *                 exchange -> {
     *                     exchange.getIn().setHeader("Key1", "Val1");
     *                     exchange.getIn().setHeader("Key2", "Val2");
     *                     exchange.getIn().setBody("the body");
     *                 })
     *         .to("direct:start")
     *         .request()
     * }
     * </pre>
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param processor the processor
     */
    FluentProducerTemplate withProcessor(Processor processor);

    /**
     * Set the processorSupplier which will be invoke to get the processor to be used for send/request.
     *
     * <b>Important:</b> You can either only use either withExchange, or withProcessor or a combination of
     * withBody/withHeaders to construct the message to be sent.
     *
     * @param processorSupplier the supplier
     */
    FluentProducerTemplate withProcessor(Supplier<Processor> processorSupplier);

    /**
     * Sets the default endpoint
     *
     * @param endpointUri the endpoint URI to send to
     */
    FluentProducerTemplate withDefaultEndpoint(String endpointUri);

    /**
     * Sets the default endpoint
     *
     * @param resolver the {@link EndpointProducerResolver} that supply the endpoint to send to.
     */
    FluentProducerTemplate withDefaultEndpoint(EndpointProducerResolver resolver);

    /**
     * Sets the default endpoint
     *
     * @param endpoint the endpoint to send to
     */
    FluentProducerTemplate withDefaultEndpoint(Endpoint endpoint);

    /**
     * Endpoint to send to
     *
     * @param endpointUri the endpoint URI to send to
     */
    default FluentProducerTemplate to(String endpointUri) {
        final CamelContext context = ObjectHelper.notNull(getCamelContext(), "camel context");

        return to(context.getEndpoint(endpointUri));
    }

    /**
     * Endpoint to send to.
     *
     * @param uri  the String formatted endpoint uri to send to
     * @param args arguments for the string formatting of the uri
     */
    default FluentProducerTemplate toF(String uri, Object... args) {
        return to(String.format(uri, args));
    }

    /**
     * Endpoint to send to
     *
     * @param resolver the {@link EndpointProducerResolver} that supply the endpoint to send to.
     */
    default FluentProducerTemplate to(EndpointProducerResolver resolver) {
        final CamelContext context = ObjectHelper.notNull(getCamelContext(), "camel context");
        final Endpoint endpoint = resolver.resolve(context);

        return to(endpoint);
    }

    /**
     * Endpoint to send to
     *
     * @param endpoint the endpoint to send to
     */
    FluentProducerTemplate to(Endpoint endpoint);

    /**
     * Send to an endpoint (InOut) returning any result output body.
     *
     * @return                         the result
     * @throws CamelExecutionException is thrown if error occurred
     */
    Object request() throws CamelExecutionException;

    /**
     * Send to an endpoint (InOut).
     *
     * @param  type                    the expected response type
     * @return                         the result
     * @throws CamelExecutionException is thrown if error occurred
     */
    <T> T request(Class<T> type) throws CamelExecutionException;

    /**
     * Sends asynchronously to the given endpoint (InOut).
     *
     * @return a handle to be used to get the response in the future
     */
    Future<Object> asyncRequest();

    /**
     * Sends asynchronously to the given endpoint (InOut).
     *
     * @param  type the expected response type
     * @return      a handle to be used to get the response in the future
     */
    <T> Future<T> asyncRequest(Class<T> type);

    /**
     * Send to an endpoint (InOnly)
     *
     * @throws CamelExecutionException is thrown if error occurred
     */
    Exchange send() throws CamelExecutionException;

    /**
     * Sends asynchronously to the given endpoint (InOnly).
     *
     * @return a handle to be used to get the response in the future
     */
    Future<Exchange> asyncSend();
}
