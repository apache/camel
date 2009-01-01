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
package org.apache.camel.component.restlet;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * Interface for converting between Camel message and Restlet message.
 * 
 * @version $Revision$
 */
public interface RestletBinding {
    
    /**
     * Populate Restlet request from Camel message
     *  
     * @param exchange message to be copied from 
     * @param response to be populated
     */
    void populateRestletResponseFromExchange(Exchange exchange,
            Response response);

    /**
     * Populate Camel message from Restlet request
     * 
     * @param request message to be copied from
     * @param exchange to be populated
     * @throws Exception 
     */
    void populateExchangeFromRestletRequest(Request request, 
            Exchange exchange) throws Exception;

    /**
     * Populate Restlet Request from Camel message
     * 
     * @param request to be populated
     * @param exchange message to be copied from
     */
    void populateRestletRequestFromExchange(Request request, Exchange exchange);

    /**
     * Populate Camel message from Restlet response
     * 
     * @param exchange to be populated
     * @param response message to be copied from
     * @throws IOException 
     */
    void populateExchangeFromRestletResponse(Exchange exchange,
            Response response) throws IOException;

}
