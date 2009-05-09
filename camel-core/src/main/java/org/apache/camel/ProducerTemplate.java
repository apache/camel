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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Template (named like Spring's TransactionTemplate & JmsTemplate
 * et al) for working with Camel and sending {@link Message} instances in an
 * {@link Exchange} to an {@link Endpoint}.
 * <p/>
 * <b>All</b> methods throws {@link RuntimeCamelException} if processing of
 * the {@link Exchange} failed and an Exception occured. The <tt>getCause</tt>
 * method on {@link RuntimeCamelException} returns the wrapper original caused
 * exception.
 * <p/>
 * All the send<b>Body</b> methods will return the content according to this strategy
 * <ul>
 *   <li>throws {@link RuntimeCamelException} as stated above</li>
 *   <li>The <tt>fault.body</tt> if there is a fault message set and its not <tt>null</tt></li>
 *   <li>Either <tt>IN</tt> or <tt>OUT</tt> body according to the message exchange pattern. If the pattern is
 *   Out capable then the <tt>OUT</tt> body is returned, otherwise <tt>IN</tt>.
 * </ul>
 * <p/>
 * <b>Important note on usage:</b> See this
 * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">FAQ entry</a>
 * before using.
 *
 * @version $Revision$
 */
public interface ProducerTemplate extends Service {

    // Synchronous methods
    // -----------------------------------------------------------------------

    /**
     * Sends the exchange to the default endpoint
     *
     * @param exchange the exchange to send
     * @return the returned exchange
     */
    Exchange send(Exchange exchange);

    /**
     * Sends an exchange to the default endpoint using a supplied processor
     *
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(Processor processor);

    /**
     * Sends the body to the default endpoint
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBody(Object body);

    /**
     * Sends the body to the default endpoint with a specified header and header
     * value
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param header the header name
     * @param headerValue the header value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeader(Object body, String header, Object headerValue);

    /**
     * Sends the body to the default endpoint with a specified property and property
     * value
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body          the payload to send
     * @param property      the property name
     * @param propertyValue the property value
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndProperty(Object body, String property, Object propertyValue);
    
    /**
     * Sends the body to the default endpoint with the specified headers and
     * header values
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param headers      the headers
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeaders(Object body, Map<String, Object> headers);

    // Allow sending to arbitrary endpoints
    // -----------------------------------------------------------------------

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange    the exchange to send
     * @return the returned exchange
     */
    Exchange send(String endpointUri, Exchange exchange);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(String endpointUri, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
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
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     * @return the returned exchange
     */
    Exchange send(Endpoint endpoint, Exchange exchange);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     * @return the returned exchange
     */
    Exchange send(Endpoint endpoint, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
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
     * Send the body to an endpoint
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint   the endpoint to send the exchange to
     * @param body       the payload
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBody(Endpoint endpoint, Object body);

    /**
     * Send the body to an endpoint
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param body          the payload
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBody(String endpointUri, Object body);

    /**
     * Send the body to an endpoint with the given {@link ExchangePattern}
     * returning any result output body
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
    Object sendBody(Endpoint endpoint, ExchangePattern pattern, Object body);

    /**
     * Send the body to an endpoint returning any result output body
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
    Object sendBody(String endpointUri, ExchangePattern pattern, Object body);

    /**
     * Sends the body to an endpoint with a specified header and header value
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
    void sendBodyAndHeader(String endpointUri, Object body, String header, Object headerValue);

    /**
     * Sends the body to an endpoint with a specified header and header value
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
    void sendBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue);

    /**
     * Sends the body to an endpoint with a specified header and header value
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
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
                             String header, Object headerValue);

    /**
     * Sends the body to an endpoint with a specified header and header value
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
                             String header, Object headerValue);

    /**
     * Sends the body to an endpoint with a specified property and property value
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
    void sendBodyAndProperty(String endpointUri, Object body, String property, Object propertyValue);

    /**
     * Sends the body to an endpoint with a specified property and property value
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
    void sendBodyAndProperty(Endpoint endpoint, Object body, String property, Object propertyValue);

    /**
     * Sends the body to an endpoint with a specified property and property value
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
                               String property, Object propertyValue);

    /**
     * Sends the body to an endpoint with a specified property and property value
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
                               String property, Object propertyValue);

    /**
     * Sends the body to an endpoint with the specified headers and header values
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers);

    /**
     * Sends the body to an endpoint with the specified headers and header values
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the endpoint URI to send to
     * @param body the payload to send
     * @param headers headers
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    void sendBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers);

    /**
     * Sends the body to an endpoint with the specified headers and header values
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
                              Map<String, Object> headers);

    /**
     * Sends the body to an endpoint with the specified headers and header values
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
                              Map<String, Object> headers);


    // Methods using an InOut ExchangePattern
    // -----------------------------------------------------------------------

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint  the Endpoint to send to
     * @param processor the processor which will populate the exchange before sending
     * @return the result (see class javadoc)
     */
    Exchange request(Endpoint endpoint, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send to
     * @param processor the processor which will populate the exchange before sending
     * @return the result (see class javadoc)
     */
    Exchange request(String endpointUri, Processor processor);

    /**
     * Sends the body to the default endpoint and returns the result content
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBody(Object body);

    /**
     * Sends the body to the default endpoint and returns the result content
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param body the payload to send
     * @param type the expected response type
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    <T> T requestBody(Object body, Class<T> type);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpoint the Endpoint to send to
     * @param body     the payload
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBody(Endpoint endpoint, Object body);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    <T> T requestBody(Endpoint endpoint, Object body, Class<T> type);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     * <p/><b>Notice:</b> that if the processing of the exchange failed with an Exception
     * it is thrown from this method as a {@link org.apache.camel.CamelExecutionException} with
     * the caused exception wrapped.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @return the result (see class javadoc)
     * @throws CamelExecutionException if the processing of the exchange failed
     */
    Object requestBody(String endpointUri, Object body);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    <T> T requestBody(String endpointUri, Object body, Class<T> type);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    Object requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    <T> T requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue, Class<T> type);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    Object requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    <T> T requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type);

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers);

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type);

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    Object requestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers);

    /**
     * Sends the body to an endpoint with the specified headers and header values.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
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
    <T> T requestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers, Class<T> type);


    // Asynchronous methods
    // -----------------------------------------------------------------------

    /**
     * Sets the executor service to use for async messaging.
     * <p/>
     * If none provided Camel will default use a {@link java.util.concurrent.ScheduledExecutorService}
     * with a pool of 5 threads.
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
    Future<Exchange> asyncSend(String endpointUri, Exchange exchange);

    /**
     * Sends an asynchronous exchange to the given endpoint.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * @return a handle to be used to get the response in the future
     */
    Future<Exchange> asyncSend(String endpointUri, Processor processor);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOnly} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @return a handle to be used to get the response in the future
     */
    Future<Object> asyncSendBody(String endpointUri, Object body);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @return a handle to be used to get the response in the future
     */
    Future<Object> asyncRequestBody(String endpointUri, Object body);

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
    Future<Object> asyncRequestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param headers     headers
     * @return a handle to be used to get the response in the future
     */
    Future<Object> asyncRequestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers);

    /**
     * Sends an asynchronous body to the given endpoint.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param body        the body to send
     * @param type        the expected response type
     * @return a handle to be used to get the response in the future
     */
    <T> Future<T> asyncRequestBody(String endpointUri, Object body, Class<T> type);

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
    <T> Future<T> asyncRequestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type);

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
    <T> Future<T> asyncRequestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type);

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
    <T> T extractFutureBody(Future future, Class<T> type);

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
    <T> T extractFutureBody(Future future, long timeout, TimeUnit unit, Class<T> type) throws TimeoutException;

}
