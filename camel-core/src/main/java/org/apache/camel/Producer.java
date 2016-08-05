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

/**
 * Provides a channel on which clients can create and invoke message exchanges
 * on an {@link Endpoint}
 * 
 * @version 
 */
public interface Producer extends Processor, Service, IsSingleton, EndpointAware {

    /**
     * Creates a new exchange to send to this endpoint
     * 
     * @return a newly created exchange
     * @deprecated use {@link Endpoint#createExchange()} - will be removed in Camel 3.0
     */
    @Deprecated
    Exchange createExchange();

    /**
     * Creates a new exchange of the given pattern to send to this endpoint
     *
     * @param pattern the exchange pattern
     * @return a newly created exchange
     * @deprecated use {@link Endpoint#createExchange(ExchangePattern)} - will be removed in Camel 3.0
     */
    @Deprecated
    Exchange createExchange(ExchangePattern pattern);

    /**
     * Creates a new exchange for communicating with this exchange using the
     * given exchange to pre-populate the values of the headers and messages
     *
     * @param exchange the existing exchange
     * @return the created exchange
     * @deprecated will be removed in Camel 3.0
     */
    @Deprecated
    Exchange createExchange(Exchange exchange);
}
