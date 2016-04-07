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
package org.apache.camel.http.common;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * A pluggable strategy for configuring the http binding so reading request and writing response
 * can be customized using the Java Servlet API.
 * <p/>
 * This is also used by the <tt>camel-jetty</tt> component in the <tt>JettyHttpConsumer</tt> class.
 */
public interface HttpBinding {

    /**
     * Strategy to read the given request and bindings it to the given message.
     *
     * @param request  the request
     * @param message  the message to populate with data from request
     */
    void readRequest(HttpServletRequest request, HttpMessage message);

    /**
     * Parses the body from a {@link org.apache.camel.http.common.HttpMessage}
     *
     * @param httpMessage the http message
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
    void writeResponse(Exchange exchange, HttpServletResponse response) throws IOException;

    /**
     * Strategy method that writes the response to the http response stream when an exception occurred
     *
     * @param exception  the exception occurred
     * @param response   the http response
     * @throws java.io.IOException can be thrown from http response
     */
    void doWriteExceptionResponse(Throwable exception, HttpServletResponse response) throws IOException;

    /**
     * Strategy method that writes the response to the http response stream for a fault message
     *
     * @param message  the fault message
     * @param response the http response
     * @param exchange the exchange to provide context for header filtering
     * @throws java.io.IOException can be thrown from http response
     */
    void doWriteFaultResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException;

    /**
     * Strategy method that writes the response to the http response stream for an OUT message
     *
     * @param message  the OUT message
     * @param response the http response
     * @param exchange the exchange to provide context for header filtering
     * @throws java.io.IOException can be thrown from http response
     */
    void doWriteResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException;

    /**
     * Should reader by used instead of input stream.
     *
     * @see #setUseReaderForPayload(boolean) for more details
     * @return <tt>true</tt> if reader should be used
     */
    boolean isUseReaderForPayload();

    /**
     * Should the {@link javax.servlet.http.HttpServletRequest#getReader()} be exposed as the payload of input messages in the Camel
     * {@link org.apache.camel.Message#getBody()} or not. If false then the {@link javax.servlet.http.HttpServletRequest#getInputStream()} will be exposed.
     * <p/>
     * Is default <tt>false</tt>.
     *
     * @param useReaderForPayload whether to use reader or not
     */
    void setUseReaderForPayload(boolean useReaderForPayload);

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back
     * serialized in the response as a application/x-java-serialized-object content type (for example using Jetty or
     * Servlet Camel components). On the producer side the exception will be deserialized and thrown as is,
     * instead of the HttpOperationFailedException. The caused exception is required to be serialized.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    boolean isTransferException();

    /**
     * Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    boolean isAllowJavaSerializedObject();

    /**
     * Whether to eager check whether the HTTP requests has content if the content-length header is 0 or not present.
     * This can be turned on in case HTTP clients do not send streamed data.
     */
    boolean isEagerCheckContentAvailable();
    
    /**
     * Whether to allow Exchange Body HTTP mapping
     */
    boolean isMapHttpMessageBody();
    
    /**
     * Whether to allow Exchange Headers HTTP mapping
     */
    boolean isMapHttpMessageHeaders();

    /**
     * Whether to eager check whether the HTTP requests has content if the content-length header is 0 or not present.
     * This can be turned on in case HTTP clients do not send streamed data.
     */
    void setEagerCheckContentAvailable(boolean eagerCheckContentAvailable);

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back
     * serialized in the response as a application/x-java-serialized-object content type (for example using Jetty or
     * Servlet Camel components). On the producer side the exception will be deserialized and thrown as is,
     * instead of the HttpOperationFailedException. The caused exception is required to be serialized.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    void setTransferException(boolean transferException);

    /**
     * Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     *
     * @param allowJavaSerializedObject <tt>true</tt> to allow serializing java objects
     */
    void setAllowJavaSerializedObject(boolean allowJavaSerializedObject);

    /**
     * Gets the header filter strategy
     *
     * @return the strategy
     */
    HeaderFilterStrategy getHeaderFilterStrategy();

    /**
     * Sets the header filter strategy to use.
     * <p/>
     * Will default use {@link org.apache.camel.http.common.HttpHeaderFilterStrategy}
     *
     * @param headerFilterStrategy the custom strategy
     */
    void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy);
    
    /**
     * Whether to allow Exchange Body HTTP mapping
     * <p/>
     * This is by default turned on. If you disable this then be aware that the Exchange body won't be mapped to HTTP
     */
    void setMapHttpMessageBody(boolean mapHttpMessageBody);
    
    /**
     * Whether to allow Exchange Headers HTTP mapping
     * <p/>
     * This is by default turned on. If you disable this then be aware that the Exchange headers won't be mapped to HTTP
     */
    void setMapHttpMessageHeaders(boolean mapHttpMessageHeaders);

}
