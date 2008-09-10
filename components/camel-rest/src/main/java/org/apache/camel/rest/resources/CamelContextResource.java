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

import com.sun.jersey.spi.inject.Inject;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.rest.model.Endpoints;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * @version $Revision: 1.1 $
 */
@Path("context")
@Singleton
public class CamelContextResource {

    private final CamelContext camelContext;

    public CamelContextResource(@Inject CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @GET
    @Produces("text/plain")
    public String getValue() {
        return "Has CamelContext: " + camelContext;
    }

    @GET
    @Path("endpoints")
    @Produces({"application/json", "application/xml"})
    public Endpoints getEndpoints() {
        Endpoints answer = new Endpoints();
        answer.load(camelContext);
        return answer;
    }

    @Path("endpoint/{id}")
    public EndpointResource findWidget(@PathParam("id") String id) {
        // TODO lets assume the ID is the endpoint
        Endpoint endpoint = getCamelContext().getEndpoint(id);
        if (endpoint != null) {
            return new EndpointResource(endpoint);
        } else {
            return null;
        }
    }

}
