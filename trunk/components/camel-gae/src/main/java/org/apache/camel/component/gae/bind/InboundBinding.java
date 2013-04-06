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
package org.apache.camel.component.gae.bind;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * Represents the binding of request and response types to an {@link Exchange}.
 * The request and response types are defined via the type parameters
 * <code>S</code> and <code>T</code>, respectively. The InboundBinding is used
 * by {@link Consumer} implementations or their clients to translate between
 * protocol-specific or services-specific messages and {@link Exchange} objects.
 * 
 * @param S request type.
 * @param T response type.
 * @param E endpoint type.
 */
public interface InboundBinding<E extends Endpoint, S, T> {

    /**
     * Populates an {@link Exchange} from request data and endpoint configuration data.
     * 
     * @param endpoint endpoint providing binding-relevant information. 
     * @param exchange exchange to be populated or created (if <code>null</code>) from request data.
     * @param request request to read data from.
     * @return the populated exchange.
     */
    Exchange readRequest(E endpoint, Exchange exchange, S request) throws Exception;
    
    /**
     * Creates or populates a response object from {@link Exchange} and endpoint configuration data.
     * 
     * @param endpoint endpoint providing binding-relevant information. 
     * @param exchange exchange to read data from.
     * @param response to be populated or created (if <code>null</code>) from exchange data.
     * @return the populated response.
     */
    T writeResponse(E endpoint, Exchange exchange, T response) throws Exception;
    
}
