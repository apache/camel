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
 * @version $Revision$
 */
public interface Producer<E extends Exchange> extends Processor, Service {

    Endpoint<E> getEndpoint();

    /**
     * Creates a new exchange to send to this endpoint
     * 
     * @return a newly created exchange
     */
    E createExchange();

    /**
     * Creates a new exchange of the given pattern to send to this endpoint
     *
     * @return a newly created exchange
     */
    E createExchange(ExchangePattern pattern);

    /**
     * Creates a new exchange for communicating with this exchange using the
     * given exchange to pre-populate the values of the headers and messages
     */
    E createExchange(E exchange);
}
