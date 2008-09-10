/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.rest.resources;

import org.apache.camel.rest.model.Endpoints;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;

/**
 * @version $Revision: 1.1 $
 */
public class EndpointResource {

    private final Endpoint endpoint;

    public EndpointResource(Endpoint endpoint) {
        this.endpoint = endpoint;
    }


    @GET
    @Produces("text/plain")
    public String getValue() {
        return  endpoint.getEndpointUri();
    }
}
