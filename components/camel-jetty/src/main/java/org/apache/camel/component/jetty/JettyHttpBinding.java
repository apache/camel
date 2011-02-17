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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * Jetty specific binding to parse the response when using {@link org.apache.camel.component.jetty.JettyHttpProducer}
 *
 * @version 
 */
public interface JettyHttpBinding {

    /**
     * Parses the response from the Jetty client.
     *
     * @param exchange  the Exchange which to populate with the response
     * @param httpExchange  the response from the Jetty client
     * @throws Exception is thrown if error parsing response
     */
    void populateResponse(Exchange exchange, JettyContentExchange httpExchange) throws Exception;

    /**
     * Gets the header filter strategy
     *
     * @return the strategy
     */
    HeaderFilterStrategy getHeaderFilterStrategy();

    /**
     * Sets the header filter strategy to use.
     * <p/>
     * Will default use {@link org.apache.camel.component.http.HttpHeaderFilterStrategy}
     *
     * @param headerFilterStrategy the custom strategy
     */
    void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy);

    /**
     * Whether to throw {@link org.apache.camel.component.http.HttpOperationFailedException}
     * in case of response code != 200.
     *
     * @param throwExceptionOnFailure <tt>true</tt> to throw exception
     */
    void setThrowExceptionOnFailure(boolean throwExceptionOnFailure);

    /**
     * Whether to throw {@link org.apache.camel.component.http.HttpOperationFailedException}
     * in case of response code != 200.
     *
     * @return <tt>true</tt> to throw exception
     */
    boolean isThrowExceptionOnFailure();

    /**
     * Whether to transfer exception back as a serialized java object
     * if processing failed due to an exception
     *
     * @param transferException <tt>true</tt> to transfer exception
     */
    void setTransferException(boolean transferException);

    /**
     * Whether to transfer exception back as a serialized java object
     * if processing failed due to an exception
     *
     * @return <tt>true</tt> to transfer exception
     */
    boolean isTransferException();

}
