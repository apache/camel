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
package org.apache.camel.web.resources;

import java.net.URI;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.representation.Form;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.model.RouteDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the status of a single single Camel Route which is used to implement one or more
 * <a href="http://camel.apache.org/enterprise-integration-patterns.html">Enterprise Integration Paterns</a>
 */
public class RouteStatusResource {

    private static final Logger LOG = LoggerFactory.getLogger(RouteStatusResource.class);
    private RouteResource routeResource;

    public RouteStatusResource(RouteResource routeResource) {
        this.routeResource = routeResource;
    }

    public RouteDefinition getRoute() {
        return routeResource.getRoute();
    }

    public CamelContext getCamelContext() {
        return routeResource.getCamelContext();
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getStatusText() {
        ServiceStatus status = getStatus();
        if (status != null) {
            return status.toString();
        }
        return null;
    }

    public ServiceStatus getStatus() {
        return getRoute().getStatus(getCamelContext());
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response setStatus(String status) throws Exception {
        if (status != null) {
            if (status.equalsIgnoreCase("start")) {
                getCamelContext().startRoute(getRoute().getId());
                return Response.ok().build();
            } else if (status.equalsIgnoreCase("stop")) {
                getCamelContext().stopRoute(getRoute().getId());
                return Response.ok().build();
            }
        }
        return Response.noContent().build();
    }


    /**
     * Sets the status of this route to either "start" or "stop"
     *
     * @param formData is the form data POSTed typically from a HTML form with the <code>status</code> field
     *                 set to either "start" or "stop"
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response setStatus(Form formData) throws Exception {
        // TODO replace the Form class with an injected bean?
        LOG.info("Received form! " + formData);
        String status = formData.getFirst("status", String.class);
        setStatus(status);
        return Response.seeOther(new URI("/routes")).build();
    }

}
