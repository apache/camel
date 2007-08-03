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

/**
 * @version $Revision: $
 */
public interface ProducerTemplate<E extends Exchange> extends Service {


    /**
     * Sends the exchange to the default endpoint
     *
     * @param exchange the exchange to send
     */
    E send(E exchange);

    /**
     * Sends an exchange to the default endpoint
     * using a supplied @{link Processor} to populate the exchange
     *
     * @param processor the transformer used to populate the new exchange
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
     * Sends the body to the default endpoint with a specified header and header value
     *
     * @param body        the payload send
     * @param header      the header name
     * @param headerValue the header value
     * @return the result
     */
    Object sendBody(Object body, String header, Object headerValue);

    /**
     * Sends the body to the default endpoint with the specified headers and header values
     *
     * @param body        the payload send
     * @return the result
     */
    Object sendBody(Object body, Map<String, Object> headers);



    // Allow sending to arbitrary endpoints
    //-----------------------------------------------------------------------

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange    the exchange to send
     */
    E send(String endpointUri, E exchange);

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     */
    E send(String endpointUri, Processor processor);

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    E send(Endpoint<E> endpoint, E exchange);

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     */
    E send(Endpoint<E> endpoint, Processor processor);

    /**
     * Send the body to an endpoint
     *
     * @param endpoint
     * @param body     = the payload
     * @return the result
     */
    Object sendBody(Endpoint<E> endpoint, Object body);

    /**
     * Send the body to an endpoint
     *
     * @param endpointUri
     * @param body        = the payload
     * @return the result
     */
    Object sendBody(String endpointUri, Object body);


}
