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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;

/**
 * Represents the binding of request and response types to an {@link Exchange}.
 * The request and response types are defined via the type parameters
 * <code>S</code> and <code>T</code>, respectively. The OutboundBinding is used
 * by {@link Producer} implementations to translate between {@link Exchange}
 * objects and protocol-specific or services-specific messages.
 * 
 * @param S request type.
 * @param T response type.
 * @param E endpoint type.
 */
public interface OutboundBinding<E extends Endpoint, S, T> {

    /**
     * Creates or populates a request object from {@link Exchange} and endpoint configuration data.
     * 
     * @param endpoint endpoint providing binding-relevant information. 
     * @param exchange exchange to read data from.
     * @param request request to be populated or created (if <code>null</code>) from exchange data.
     * @return the populated response.
     */
    S writeRequest(E endpoint, Exchange exchange, S request) throws Exception;
    
    /**
     * Populates an {@link Exchange} from response data and endpoint configuration data.
     * 
     * @param endpoint endpoint providing binding-relevant information. 
     * @param exchange exchange to be populated or created (if <code>null</code>) from response data.
     * @param response response to read data from.
     * @return the populated exchange.
     */
    Exchange readResponse(E endpoint, Exchange exchange, T response) throws Exception;
    
}
