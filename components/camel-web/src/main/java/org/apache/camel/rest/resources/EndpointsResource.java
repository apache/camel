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

import com.sun.jersey.api.representation.Form;
import com.sun.jersey.api.view.ImplicitProduces;
import com.sun.jersey.api.view.Viewable;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.rest.model.EndpointLink;
import org.apache.camel.rest.model.Endpoints;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
//@ImplicitProduces(Constants.HTML_MIME_TYPES)
public class EndpointsResource extends CamelChildResourceSupport {
    private String error = "";
    private String newUri = "mock:someName";

    public EndpointsResource(CamelContextResource contextResource) {
        super(contextResource);
    }

    /**
     * Returns a list of endpoints available in this context
     *
     * @return
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Endpoints getDTO() {
        return new Endpoints(camelContext);
    }


    /**
     * Looks up an individual endpoint
     */
    @Path("{id}")
    public EndpointResource getEndpoint(@PathParam("id") String id) {
        // TODO lets assume the ID is the endpoint
        Endpoint endpoint = null;
        if (id != null) {
            // lets remove any whitespace
            id = id.trim();
            if (id.length() > 0) {
                endpoint = camelContext.getEndpoint(id);
            }
        }
        if (endpoint != null) {
            return new EndpointResource(camelContext, template, endpoint);
        } else {
            return null;
        }
    }


    // Creating endpoints
    //-------------------------------------------------------------------------

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response postUri(String uri) throws URISyntaxException {
        EndpointResource endpoint = getEndpoint(uri);
        if (endpoint != null) {
            return Response.ok().build();
        }
        return Response.noContent().build();
    }

    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response postUriForm(@Context UriInfo uriInfo, Form formData) throws URISyntaxException {
        System.out.println("Received form! " + formData);
        newUri = formData.getFirst("uri", String.class);
        EndpointResource endpoint = getEndpoint(newUri);
        if (endpoint != null) {
            String href = endpoint.getHref();
            System.out.println("Created endpoint so redirecting to " + href);
            return Response.seeOther(new URI(href)).build();
        } else {
            error = "Could not find a component to resolve that URI";

            System.out.println("Failed to create new endpoint!");

            // lets re-render the form
            return Response.ok(new Viewable("index", this)).build();
        }
    }


    // Properties
    //-------------------------------------------------------------------------

    public List<EndpointLink> getEndpoints() {
        return getDTO().getEndpoints();
    }

    public String getError() {
        return error;
    }

    public String getNewUri() {
        return newUri;
    }
}
