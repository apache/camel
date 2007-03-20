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


/**
 * Represents an endpoint that can send and receive message exchanges
 *
 * @version $Revision$
 */
public interface Endpoint<E> extends Processor<E> {

    /**
     * Returns the string representation of the endpoint URI
     */
    public String getEndpointUri();

    /**
     * Sends an outbound exchange to the endpoint
     */
    void onExchange(E exchange);
    
    /**
     * Create a new exchange for communicating with this endpoint
     */
    E createExchange();

    /**
     * Called by the container to Activate the endpoint.  Once activated,
     * the endpoint will start delivering inbound message exchanges
     * that are received to the specified processor.
     *
     * The processor must be thread safe ( or stateless ) since some endpoints 
     * may choose to deliver exchanges concurrently to the processor.
     * 
     * @throws IllegalStateException if the Endpoint has already been activated.
     */
	void activate(Processor<E> processor) throws IllegalStateException;

    /**
     * Called by the container when the endpoint is deactivated
     */
    void deactivate();

    /**
     * Returns the context which created the endpoint
     *
     * @return the context which created the endpoint
     */
    CamelContext getContext();
}
