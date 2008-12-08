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
package org.apache.camel.component.http;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * A plugable strategy for configuring the http binding so reading request and writing response
 * can be customized using the Java Servlet API.
 * <p/>
 * This is used by the camel-jetty.
 *
 * @version $Revision$
 */
public interface HttpBinding {

    /**
     * Startegy to read the given request and bindings it to the given message.
     *
     * @param request  the request
     * @param message  the message to populate with data from request
     */
    void readRequest(HttpServletRequest request, HttpMessage message);

    /**
     * Parses the body from a {@link org.apache.camel.component.http.HttpMessage}
     *
     * @return the parsed body returned as either a {@link java.io.InputStream} or a {@link java.io.Reader}
     * depending on the {@link #setUseReaderForPayload(boolean)} property.
     * @throws java.io.IOException can be thrown
     */
    Object parseBody(HttpMessage httpMessage) throws IOException;

    /**
     * Writes the exchange to the servlet response.
     * <p/>
     * Default implementation will delegate to the following methods depending on the status of the exchange
     * <ul>
     *   <li>doWriteResponse - processing returns a OUT message </li>
     *   <li>doWriteFaultResponse - processing returns a fault message</li>
     *   <li>doWriteResponse - processing returns an exception and status code 500</li>
     * </ul>
     *
     * @param exchange the exchange
     * @param response the http response
     * @throws java.io.IOException can be thrown from http response
     */
    void writeResponse(HttpExchange exchange, HttpServletResponse response) throws IOException;

    /**
     * Strategy method that writes the response to the http response stream when an exception occuerd
     *
     * @param exception  the exception occured
     * @param response   the http response
     * @throws java.io.IOException can be thrown from http response
     */
    void doWriteExceptionResponse(Throwable exception, HttpServletResponse response) throws IOException;

    /**
     * Strategy method that writes the response to the http response stream for a fault message
     *
     * @param message  the fault message
     * @param response   the http response
     * @throws java.io.IOException can be thrown from http response
     */
    void doWriteFaultResponse(Message message, HttpServletResponse response) throws IOException;

    /**
     * Strategy method that writes the response to the http response stream for an OUT message
     *
     * @param message  the OUT message
     * @param response   the http response
     * @throws java.io.IOException can be thrown from http response
     */
    void doWriteResponse(Message message, HttpServletResponse response) throws IOException;

    boolean isUseReaderForPayload();

    /**
     * Should the {@link javax.servlet.http.HttpServletRequest#getReader()} be exposed as the payload of input messages in the Camel
     * {@link org.apache.camel.Message#getBody()} or not. If false then the {@link javax.servlet.http.HttpServletRequest#getInputStream()} will be exposed.
     */
    void setUseReaderForPayload(boolean useReaderForPayload);

    HeaderFilterStrategy getHeaderFilterStrategy();

    /**
     * Sets the header filter stratety to use.
     * <p/>
     * Will default use {@link org.apache.camel.component.http.HttpHeaderFilterStrategy}
     */
    void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy);

}
