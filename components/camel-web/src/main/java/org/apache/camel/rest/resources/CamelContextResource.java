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
package org.apache.camel.rest.resources;


import com.sun.jersey.api.view.ImplicitProduces;
import com.sun.jersey.spi.inject.Inject;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.RoutesType;
import org.apache.camel.rest.model.Camel;
import org.apache.camel.rest.model.EndpointLink;

import javax.annotation.PreDestroy;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;


/**
 * The resource for the CamelContext
 *
 * @version $Revision$
 */
@Path("/")
@ImplicitProduces(Constants.HTML_MIME_TYPES)
@Singleton
public class CamelContextResource {

    private CamelContext camelContext;
    private ProducerTemplate template;

    public CamelContextResource(@Inject CamelContext camelContext) throws Exception {
        this.camelContext = camelContext;
        this.template = camelContext.createProducerTemplate();
        template.start();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public ProducerTemplate getTemplate() {
        return template;
    }

    public String getName() {
        return camelContext.getName();
    }

    @PreDestroy
    public void close() throws Exception {
        if (template != null) {
            template.stop();
        }
    }

    // XML / JSON representations
    //-------------------------------------------------------------------------

    @GET
    // TODO we can replace this long expression with a static constant
    // when Jersey supports JAX-RS 1.1
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Camel getCamel() {
        return new Camel(camelContext);
    }

    @Path("endpoints")
    public EndpointsResource getEndpointsResource() {
        return new EndpointsResource(this);
    }

    public List<EndpointLink> getEndpoints() {
        return getEndpointsResource().getDTO().getEndpoints();
    }

    /**
     * Returns the routes currently active within this context
     *
     * @return
     */
    @GET
    @Path("routes")
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public RoutesType getRouteDefinitions() {
        RoutesType answer = new RoutesType();
        if (camelContext != null) {
            List<RouteType> list = camelContext.getRouteDefinitions();
            answer.setRoutes(list);
        }
        return answer;
    }

    // Properties
    //-------------------------------------------------------------------------
    public List<RouteType> getRoutes() {
        return getRouteDefinitions().getRoutes();
    }

}
