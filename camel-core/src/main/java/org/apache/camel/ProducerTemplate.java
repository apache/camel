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

/**
 * Template (named like Spring's TransactionTemplate & JmsTemplate
 * et al) for working with Camel and sending {@link Message} instances in an
 * {@link Exchange} to an {@link Endpoint}.
 *
 * @version $Revision$
 */
public interface ProducerTemplate<E extends Exchange> extends Service {

    /**
     * Sends the exchange to the default endpoint
     *
     * @param exchange the exchange to send
     */
    E send(E exchange);

    /**
     * Sends an exchange to the default endpoint using a supplied
     *
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     */
    E send(Processor processor);

    /**
     * Sends the body to the default endpoint and returns the result content
     *
     * @param body the body to send
     * @return the returned message body
     */
    Object sendBody(Object body);

    /**
     * Sends the body to the default endpoint with a specified header and header
     * value
     *
     * @param body        the payload send
     * @param header      the header name
     * @param headerValue the header value
     * @return the result
     */
    Object sendBodyAndHeader(Object body, String header, Object headerValue);

    /**
     * Sends the body to the default endpoint with the specified headers and
     * header values
     *
     * @param body the payload send
     * @return the result
     */
    Object sendBodyAndHeaders(Object body, Map<String, Object> headers);

    // Allow sending to arbitrary endpoints
    // -----------------------------------------------------------------------

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange    the exchange to send
     */
    E send(String endpointUri, E exchange);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     */
    E send(String endpointUri, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param pattern     the message {@link ExchangePattern} such as
     *                    {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor   the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     */
    E send(String endpointUri, ExchangePattern pattern, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange.
     * @param callback    the callback will be called when the exchange is completed.
     */
    E send(String endpointUri, Processor processor, AsyncCallback callback);

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    E send(Endpoint<E> endpoint, E exchange);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     */
    E send(Endpoint<E> endpoint, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param pattern   the message {@link ExchangePattern} such as
     *                  {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange
     */
    E send(Endpoint<E> endpoint, ExchangePattern pattern, Processor processor);

    /**
     * Sends an exchange to an endpoint using a supplied processor
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     * {@link Processor} to populate the exchange.
     * @param callback  the callback will be called when the exchange is completed.
     */
    E send(Endpoint<E> endpoint, Processor processor, AsyncCallback callback);

    /**
     * Send the body to an endpoint returning any result output body
     *
     * @param endpoint   the endpoint to send the exchange to
     * @param body       the payload
     * @return the result
     */
    Object sendBody(Endpoint<E> endpoint, Object body);

    /**
     * Send the body to an endpoint returning any result output body
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param body          the payload
     * @return the result
     */
    Object sendBody(String endpointUri, Object body);

    /**
     * Send the body to an endpoint with the given {@link ExchangePattern}
     * returning any result output body
     *
     * @param endpoint      the endpoint to send the exchange to
     * @param body          the payload
     * @param pattern       the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @return the result
     */
    Object sendBody(Endpoint<E> endpoint, ExchangePattern pattern, Object body);

    /**
     * Send the body to an endpoint returning any result output body
     *
     * @param endpointUri   the endpoint URI to send the exchange to
     * @param pattern       the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body          the payload
     * @return the result
     */
    Object sendBody(String endpointUri, ExchangePattern pattern, Object body);

    /**
     * Sends the body to an endpoint with a specified header and header value
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload send
     * @param header the header name
     * @param headerValue the header value
     * @return the result
     */
    Object sendBodyAndHeader(String endpointUri, Object body, String header,
                                    Object headerValue);

    /**
     * Sends the body to an endpoint with a specified header and header value
     *
     * @param endpoint the Endpoint to send to
     * @param body the payload send
     * @param header the header name
     * @param headerValue the header value
     * @return the result
     */
    Object sendBodyAndHeader(Endpoint endpoint, Object body, String header,
                                    Object headerValue);

    /**
     * Sends the body to an endpoint with a specified header and header value
     *
     * @param endpoint the Endpoint to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload send
     * @param header the header name
     * @param headerValue the header value
     * @return the result
     */
    Object sendBodyAndHeader(Endpoint endpoint, ExchangePattern pattern, Object body, String header,
                                    Object headerValue);

    /**
     * Sends the body to an endpoint with a specified header and header value
     *
     * @param endpoint the Endpoint URI to send to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param body the payload send
     * @param header the header name
     * @param headerValue the header value
     * @return the result
     */
    Object sendBodyAndHeader(String endpoint, ExchangePattern pattern, Object body, String header,
                                    Object headerValue);

    /**
     * Sends the body to an endpoint with the specified headers and header
     * values
     *
     * @param endpointUri the endpoint URI to send to
     * @param body the payload send
     * @return the result
     */
    Object sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers);

    /**
     * Sends the body to an endpoint with the specified headers and header
     * values
     *
     * @param endpoint the endpoint URI to send to
     * @param body the payload send
     * @return the result
     */
    Object sendBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers);


    // Methods using an InOut ExchangePattern
    // -----------------------------------------------------------------------

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint  the Endpoint to send to
     * @param processor the processor which will populate the exchange before sending
     * @return the result
     */
    E request(Endpoint<E> endpoint, Processor processor);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint the Endpoint to send to
     * @param body     the payload
     * @return the result
     */
    Object requestBody(Endpoint<E> endpoint, Object body);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpoint    the Endpoint to send to
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @return the result
     */
    Object requestBodyAndHeader(Endpoint<E> endpoint, Object body, String header, Object headerValue);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send to
     * @param processor the processor which will populate the exchange before sending
     * @return the result
     */
    E request(String endpointUri, Processor processor);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @return the result
     */
    Object requestBody(String endpointUri, Object body);

    /**
     * Send the body to an endpoint returning any result output body.
     * Uses an {@link ExchangePattern#InOut} message exchange pattern.
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload
     * @param header      the header name
     * @param headerValue the header value
     * @return the result
     */
    Object requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue);

}
