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
package org.apache.camel.component.resteasy;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * Strategy interface for implementing binding between Resteasy and Camel
 *
 */
public interface ResteasyHttpBinding {

    /**
     * Setter method for HeaderFilterStrategy
     *
     * @param headerFilterStrategy header filter strategy which should be used in ResteasyHttpBinding
     */
    void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy);

    /**
     * Populate Resteasy request from Camel exchange and execute it in Resteasy client
     *
     * @param uri URI used for client request
     * @param exchange message to be copied from
     * @param parameters to be used in Resteasy request configuration
     * @return response from the server to which was the request sent
     */
    Response populateResteasyRequestFromExchangeAndExecute(String uri, Exchange exchange, Map<String, String> parameters);

    /**
     * Populate Resteasy request from Camel exchange and execute it as Resteasy proxy client
     *
     * @param uri URI used for client request
     * @param exchange message to be copied from
     * @param parameters  to be used in Resteasy request configuration
     */
    void populateProxyResteasyRequestAndExecute(String uri, Exchange exchange, Map<String, String> parameters);

    /**
     * Populate Camel exchange from Resteasy response
     *
     * @param exchange to be populated
     * @param response message to be copied from
     */
    void populateExchangeFromResteasyResponse(Exchange exchange, Response response);
}
