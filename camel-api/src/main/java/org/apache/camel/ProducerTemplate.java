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
package org.apache.camel;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.spi.Synchronization;

/**
 * Template for working with Camel and sending {@link Message} instances in an
 * {@link Exchange} to an {@link Endpoint}.
 * <br/>
 * <p/><b>Important:</b> Read the javadoc of each method carefully to ensure the behavior of the method is understood.
 * Some methods is for <tt>InOnly</tt>, others for <tt>InOut</tt> MEP. And some methods throws
 * {@link org.apache.camel.CamelExecutionException} while others stores any thrown exception on the returned
 * {@link Exchange}.
 * <br/>
 * <p/>The {@link ProducerTemplate} is <b>thread safe</b>.
 * <br/>
 * <p/>All the methods which sends a message may throw {@link FailedToCreateProducerException} in
 * case the {@link Producer} could not be created. Or a {@link NoSuchEndpointException} if the endpoint could
 * not be resolved. There may be other related exceptions being thrown which occurs <i>before</i> the {@link Producer}
 * has started sending the message.
 * <br/>
 * <p/>All the sendBody or requestBody methods will return the content according to this strategy:
 * <ul>
 *   <li>throws {@link org.apache.camel.CamelExecutionException} if processing failed <i>during</i> routing
 *       with the caused exception wrapped</li>
 *   <li>The <tt>fault.body</tt> if there is a fault message set and its not <tt>null</tt></li>
 *   <li>Either <tt>IN</tt> or <tt>OUT</tt> body according to the message exchange pattern. If the pattern is
 *   Out capable then the <tt>OUT</tt> body is returned, otherwise <tt>IN</tt>.
 * </ul>
 * <br/>
 * <p/>Before using the template it must be started.
 * And when you are done using the template, make sure to {@link #stop()} the template.
 * <br/>
 * <p/><b>Important note on usage:</b> See this
 * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">FAQ entry</a>
 * before using.
 *
 * @see FluentProducerTemplate
 * @see ConsumerTemplate
 */
public interface ProducerTemplate extends Service {

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
     * Reports if async* methods will dispath processing from the calling thread (false) or through executor (true).
     * In both cases asynchronous engine will be used, so this non-threaded can be useful for high-speed
     * non-blocking processing.
     * @return if async* methods will dipatch processing with the executor
     */
    boolean isThreadedAsyncMode();

    /**
     * Reports if async* methods will dispath processing from the calling thread (false) or through executor (true).
     * In both cases asynchronous engine will be used, so this non-threaded can be useful for high-speed
     * non-blocking processing.
     * @param useExecutor if async* methods will dipatch processing with the executor
     */
    void setThreadedAsyncMode(boolean useExecutor);
    
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
     *  @param endpointUri the default endpoint uri
     */
    void setDefaultEndpointUri(String endpointUri);

    /**
     * Sets whether the {@link org.apache.camel.spi.EventNotifier} should be
     * used by this {@link ProducerTemplate} to send events about the {@link Exchange}
     * being sent.
     * <p/>
     * By default this is enabled.
     *
     * @param enabled <tt>true</tt> to enable, <tt>false</tt> to disable.
     */
    void setEventNotifierEnabled(boolean enabled);

    /**
     * Whether the {@link org.apache.camel.spi.EventNotifier} should be
     * used by this {@link ProducerTemplate} to send events about the {@link Exchange}
     * being sent.
     *
     * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise
     */
    boolean isEventNotifierEnabled();

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

    // Synchronous methods
    // -----------------------------------------------------------------------

    /**
     * Sends the exchange to the default endpoint
     * <br/><br/>
     * <b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param exchange the exchange to send
     * @return the returned exchange
     */
    Exchange send(Exchange exchange);

    /**
     * Sends an exchange to the default endpoint using a supplied processor
     * <br/><br/>
     * <b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(Processor processor);

    /**
     * Sends the body to the default endpoint
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBody(Object body) throws CamelExecutionException;

    /**
     * Sends the body to the default endpoint with a specified header and header value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param header the header name
     * @param headerValue the header value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeader(Object body, String header, Object headerValue) throws CamelExecutionException;

    /**
     * Sends the body to the default endpoint with a specified property and property value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body          the payload to send
     * @param property      the property name
     * @param propertyValue the property value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndProperty(Object body, String property, Object propertyValue) throws CamelExecutionException;
    
    /**
     * Sends the body to the default endpoint with the specified headers and header values
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param headers      the headers
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeaders(Object body, Map<String, Object> headers) throws CamelExecutionException;

    // Allow sending to arbitrary endpoints
    // -----------------------------------------------------------------------

    /**
     * Sends the exchange to the given endpoint
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange    the exchange to send
     * @return the returned exchange
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Exchange send(String endpointUri, Exchange exchange);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Exchange send(String endpointUri, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param pattern     the message {@link ExchangePattern} such as
     *                    {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor   the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(String endpointUri, ExchangePattern pattern, Processor processor);

    /**
     * Sends the exchange to the given endpoint
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     * @return the returned exchange
     */
    Exchange send(Endpoint endpoint, Exchange exchange);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(Endpoint endpoint, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param pattern   the message {@link ExchangePattern} such as
     *                  {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor);


    /**
     * Sends an exchange to an endpoint using a supplied processor
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param pattern   the message {@link ExchangePattern} such as
     *                  {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor the transformer used to populate the new exchange
     * @param resultProcessor a processor to process the exchange when the send is complete.
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor);

    /**
     * Send the body to an endpoint
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint   the endpoint to send the exchange to
     * @param body       the payload
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBody(Endpoint endpoint, Object body) throws CamelExecutionException;

    /**
     * Send the body to an endpoint
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param body          the payload
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBody(String endpointUri, Object body) throws CamelExecutionException;

    /**
     * Send the body to an endpoint with the given {@link ExchangePattern}
     * returning any result output body
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint      the endpoint to send the exchange to
     * @param body          the payload
     * @param pattern       the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBody(Endpoint endpoint, ExchangePattern pattern, Object body) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param pattern       the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body          the payload
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBody(String endpointUri, ExchangePattern pattern, Object body) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified header and header value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload to send
     * @param header the header name
     * @param headerValue the header value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeader(String endpointUri, Object body, String header, Object headerValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified header and header value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param body the payload to send
     * @param header the header name
     * @param headerValue the header value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified header and header value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload to send
     * @param header the header name
     * @param headerValue the header value
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBodyAndHeader(Endpoint endpoint, ExchangePattern pattern, Object body,
                             String header, Object headerValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified header and header value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint URI to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload to send
     * @param header the header name
     * @param headerValue the header value
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBodyAndHeader(String endpoint, ExchangePattern pattern, Object body,
                             String header, Object headerValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified property and property value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload to send
     * @param property the property name
     * @param propertyValue the property value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndProperty(String endpointUri, Object body, String property, Object propertyValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified property and property value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param body the payload to send
     * @param property the property name
     * @param propertyValue the property value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndProperty(Endpoint endpoint, Object body, String property, Object propertyValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified property and property value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload to send
     * @param property the property name
     * @param propertyValue the property value
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBodyAndProperty(Endpoint endpoint, ExchangePattern pattern, Object body,
                               String property, Object propertyValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with a specified property and property value
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint URI to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload to send
     * @param property the property name
     * @param propertyValue the property value
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBodyAndProperty(String endpoint, ExchangePattern pattern, Object body,
                               String property, Object propertyValue) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload to send
     * @param headers headers
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBodyAndHeaders(String endpointUri, ExchangePattern pattern, Object body,
                              Map<String, Object> headers) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the endpoint URI to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload to send
     * @param headers headers
     * @return the result if {@link ExchangePattern} is OUT capable, otherwise <tt>null</tt>
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object sendBodyAndHeaders(Endpoint endpoint, ExchangePattern pattern, Object body,
                              Map<String, Object> headers) throws CamelExecutionException;


    // Methods using an InOut ExchangePattern
    // -----------------------------------------------------------------------

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpoint  the Endpoint to send to
     * @param processor the processor which will populate the exchange before sending
     * @return the result (see class javadoc)
     */
    Exchange request(Endpoint endpoint, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is <b>not</b> thrown from this method, but you can access it from the returned exchange using
     * {@link org.apache.camel.Exchange#getException()}.
     *
     * @param endpointUri the endpoint URI to send to
     * @param processor the processor which will populate the exchange before sending
     * @return the result (see class javadoc)
     */
    Exchange request(String endpointUri, Processor processor);

    /**
     * Sends the body to the default endpoint and returns the result content
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBody(Object body) throws CamelExecutionException;

    /**
     * Sends the body to the default endpoint and returns the result content
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param type the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBody(Object body, Class<T> type) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param body     the payload
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBody(Endpoint endpoint, Object body) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param body     the payload
     * @param type     the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBody(Endpoint endpoint, Object body, Class<T> type) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBody(String endpointUri, Object body) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @param type        the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBody(String endpointUri, Object body, Class<T> type) throws CamelExecutionException;

    /**
     * Sends the body to the default endpoint and returns the result content
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBodyAndHeader(Object body, String header, Object headerValue) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint    the Endpoint to send to
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint    the Endpoint to send to
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @param type        the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue, Class<T> type) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue) throws CamelExecutionException;

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @param type        the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @param type the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers) throws CamelExecutionException;

    /**
     * Sends the body to the default endpoint and returns the result content
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param headers headers
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBodyAndHeaders(Object body, Map<String, Object> headers) throws CamelExecutionException;

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <br/><br/>
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @param type the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers, Class<T> type) throws CamelExecutionException;


    // Asynchronous methods
    // -----------------------------------------------------------------------

    /**
     * Sets a custom executor service to use for async messaging.
     *
     * @param executorService  the executor service.
     */
    void setExecutorService(ExecutorService executorService);

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange    the exchange to send
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncSend(String endpointUri, Exchange exchange);

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncSend(String endpointUri, Processor processor);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOnly} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncSendBody(String endpointUri, Object body);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncRequestBody(String endpointUri, Object body);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param header      the header name
     * @param headerValue the header value
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncRequestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param headers     headers
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncRequestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> CompletableFuture<T> asyncRequestBody(String endpointUri, Object body, Class<T> type);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param header      the header name
     * @param headerValue the header value
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> CompletableFuture<T> asyncRequestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param headers     headers
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> CompletableFuture<T> asyncRequestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type);

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param exchange    the exchange to send
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncSend(Endpoint endpoint, Exchange exchange);

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncSend(Endpoint endpoint, Processor processor);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOnly} message exchange pattern.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param body        the body to send
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncSendBody(Endpoint endpoint, Object body);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param body        the body to send
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncRequestBody(Endpoint endpoint, Object body);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint the endpoint to send the exchange to
     * @param body        the body to send
     * @param header      the header name
     * @param headerValue the header value
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncRequestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param body        the body to send
     * @param headers     headers
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncRequestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param body        the body to send
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> CompletableFuture<T> asyncRequestBody(Endpoint endpoint, Object body, Class<T> type);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param body        the body to send
     * @param header      the header name
     * @param headerValue the header value
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> CompletableFuture<T> asyncRequestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue, Class<T> type);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint    the endpoint to send the exchange to
     * @param body        the body to send
     * @param headers     headers
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> CompletableFuture<T> asyncRequestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers, Class<T> type);

    /**
     * Gets the response body from the future handle, will wait until the response is ready.
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param future      the handle to get the response
     * @param type        the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T extractFutureBody(Future<?> future, Class<T> type) throws CamelExecutionException;

    /**
     * Gets the response body from the future handle, will wait at most the given time for the response to be ready.
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param future      the handle to get the response
     * @param timeout     the maximum time to wait
     * @param unit        the time unit of the timeout argument
     * @param type        the expected response type
     * @return the result (see class javadoc)
     * @throws java.util.concurrent.TimeoutException if the wait timed out
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T extractFutureBody(Future<?> future, long timeout, TimeUnit unit, Class<T> type) throws TimeoutException, CamelExecutionException;

    // Asynchronous methods with callback
    // -----------------------------------------------------------------------

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param exchange      the exchange to send
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncCallback(String endpointUri, Exchange exchange, Synchronization onCompletion);

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpoint      the endpoint to send the exchange to
     * @param exchange      the exchange to send
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncCallback(Endpoint endpoint, Exchange exchange, Synchronization onCompletion);

    /**
     * Sends an asynchronous exchange to the given endpoint using a supplied processor.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param processor     the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncCallback(String endpointUri, Processor processor, Synchronization onCompletion);

    /**
     * Sends an asynchronous exchange to the given endpoint using a supplied processor.
     *
     * @param endpoint      the endpoint to send the exchange to
     * @param processor     the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Exchange> asyncCallback(Endpoint endpoint, Processor processor, Synchronization onCompletion);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOnly} message exchange pattern.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param body          the body to send
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncCallbackSendBody(String endpointUri, Object body, Synchronization onCompletion);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOnly} message exchange pattern.
     *
     * @param endpoint      the endpoint to send the exchange to
     * @param body          the body to send
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncCallbackSendBody(Endpoint endpoint, Object body, Synchronization onCompletion);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param body          the body to send
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncCallbackRequestBody(String endpointUri, Object body, Synchronization onCompletion);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint      the endpoint to send the exchange to
     * @param body          the body to send
     * @param onCompletion  callback invoked when exchange has been completed
     * @return a handle to be used to get the response in the future
     */
    CompletableFuture<Object> asyncCallbackRequestBody(Endpoint endpoint, Object body, Synchronization onCompletion);

}
