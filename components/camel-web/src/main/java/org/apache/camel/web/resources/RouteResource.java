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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.sun.jersey.api.representation.Form;
import com.sun.jersey.api.view.Viewable;

import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.RouteDotGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A single Camel Route which is used to implement one or more
 * <a href="http://camel.apache.org/enterprise-integration-patterns.html">Enterprise Integration Paterns</a>
 *
 * @version $Revision$
 */
public class RouteResource extends CamelChildResourceSupport {
    private static final transient Log LOG = LogFactory.getLog(RouteResource.class);

    private RouteDefinition route;
    private String error = "";
    private String id;


    public RouteResource(RoutesResource routesResource, RouteDefinition route) {
        super(routesResource.getContextResource());
        this.route = route;
        this.id = route.idOrCreate();
    }

    /**
     * Returns the XML or JSON representation of this route
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public RouteDefinition getRoute() {
        return route;
    }

    /**
     * Returns the XML text
     */
    public String getRouteXml() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Constants.JAXB_PACKAGES);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        // TODO fix to use "" namespace prefix
        // using this https://jaxb.dev.java.net/nonav/2.1.10/docs/vendorProperties.html#prefixmapper
        StringWriter buffer = new StringWriter();
        marshaller.marshal(route, buffer);
        return buffer.toString();
    }

    /**
     * Returns the Graphviz DOT <a href="http://camel.apache.org/visualisation.html">Visualisation</a>
     * of this route
     */
    @GET
    @Produces(Constants.DOT_MIMETYPE)
    public String getDot() throws IOException {
        RouteDotGenerator generator = new RouteDotGenerator("/tmp/camel");
        return generator.getRoutesText(getCamelContext());
    }


    /**
     * Allows a route definition to be updated
     */
    @POST
    @Consumes()
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public void postRoute(RouteDefinition routeDefinition) throws Exception {
        // lets preserve the ID
        routeDefinition.setId(id);

        // lets install the updated route
        getCamelContext().addRouteDefinitions(Collections.singletonList(routeDefinition));
    }


    /**
     * Updates a route definition using form encoded data from a web form
     *
     * @param formData is the form data POSTed typically from a HTML form with the <code>route</code> field used to encode
     *                 the XML text of the new route definition
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response postRouteForm(@Context UriInfo uriInfo, Form formData) throws URISyntaxException {
        // TODO replace the Form class with an injected bean?
        String xml = formData.getFirst("route", String.class);
        if (LOG.isDebugEnabled()) {
            LOG.debug("new XML is: " + xml);
        }
        if (xml == null) {
            error = "No XML submitted!";
        } else {
            try {
                JAXBContext context = JAXBContext.newInstance(Constants.JAXB_PACKAGES);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                Object value = unmarshaller.unmarshal(new StringReader(xml));
                if (value instanceof RouteDefinition) {
                    RouteDefinition routeDefinition = (RouteDefinition) value;
                    postRoute(routeDefinition);
                    return Response.seeOther(new URI("/routes")).build();
                } else {
                    error = "Posted XML is not a route but is of type " + ObjectHelper.className(value);
                }
            } catch (JAXBException e) {
                error = "Failed to parse XML: " + e.getMessage();
            } catch (Exception e) {
                error = "Failed to install route: " + e.getMessage();
            }
        }
        // lets re-render the form
        return Response.ok(new Viewable("edit", this)).build();
    }

    /**
     * Looks up an individual route
     */
    @Path("status")
    public RouteStatusResource getRouteStatus() {
        return new RouteStatusResource(this);
    }

    public String getError() {
        return error;
    }

}
