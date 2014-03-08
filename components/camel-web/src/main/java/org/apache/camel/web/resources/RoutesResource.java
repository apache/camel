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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.sun.jersey.api.view.Viewable;

import org.apache.camel.CamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.RouteDotGenerator;
import org.apache.camel.web.model.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The active routes in Camel which are used to implement one or more <a
 * href="http://camel.apache.org/enterprise-integration-patterns.html"
 * >Enterprise Integration Patterns</a>
 */
public class RoutesResource extends CamelChildResourceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RoutesResource.class);
    private String error = "";
    
    public RoutesResource(CamelContextResource contextResource) {
        super(contextResource);
    }

    /**
     * Returns the routes currently active within this context
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @SuppressWarnings("deprecation")
    public RoutesDefinition getRouteDefinitions() {
        RoutesDefinition answer = new RoutesDefinition();
        CamelContext camelContext = getCamelContext();
        if (camelContext != null) {
            List<RouteDefinition> list = camelContext.getRouteDefinitions();
            answer.setRoutes(list);
        }
        return answer;
    }

    /**
     * Returns the Graphviz DOT <a
     * href="http://camel.apache.org/visualisation.html">Visualisation</a> of
     * the current Camel routes
     */
    @GET
    @Produces(Constants.DOT_MIMETYPE)
    public String getDot() throws IOException {
        RouteDotGenerator generator = new RouteDotGenerator("/tmp/camel");
        return generator.getRoutesText(getCamelContext());
    }

    /**
     * Looks up an individual route
     */
    @Path("{id}")
    public RouteResource getRoute(@PathParam("id") String id) {
        List<RouteDefinition> list = getRouteDefinitions().getRoutes();
        for (RouteDefinition routeType : list) {
            if (routeType.getId().equals(id)) {
                return new RouteResource(this, routeType);
            }
        }
        return null;
    }

    /**
     * Looks up an individual route with specified language
     */
    @Path("{id}/lang/{language}")
    public RouteResource getRoute(@PathParam("id") String id, @PathParam("language") String language) {
        RouteResource routeResource = getRoute(id);
        if (routeResource != null) {
            routeResource.setLanguage(language);
        }
        return routeResource;
    }

    @Path("{id}/status")
    public RouteStatusResource getRouteStatus(@PathParam("id") String id) {
        RouteResource routeResource = getRoute(id);
        return routeResource.getRouteStatus();
    }

    /**
     * Creates a new route using form encoded data from a web form
     * 
     * @param language is the edited language used on this route
     * @param body the route definition content
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response postRouteForm(@Context UriInfo uriInfo, @FormParam("language") String language, 
                                  @FormParam("route") String body) throws URISyntaxException {

        LOG.debug("New Route is: {}", body);
        
        LOG.info(body);
        if (body == null) {
            error = "No Route submitted!";
        } else if (language.equals(RouteResource.LANGUAGE_XML)) {
            return parseXml(body);
        }
        
        error = "Not supported language!";
        return Response.ok(new Viewable("edit", this)).build();
    }
    
    // Properties
    // -------------------------------------------------------------------------

    public List<Route> getRoutes() {
        List<Route> answer = new ArrayList<Route>();
        for (RouteDefinition def : getRouteDefinitions().getRoutes()) {
            Route route = new Route(getCamelContext(), def);
            answer.add(route);
        }
        return answer;
    }
    
    public String getError() {
        return error;
    }
    
    /**
     * process the route configuration defined in Xml
     */
    private Response parseXml(String xml) {
        try {
            JAXBContext context = JAXBContext.newInstance(Constants.JAXB_PACKAGES);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            Object value = unmarshaller.unmarshal(new StringReader(xml));
            if (value instanceof RouteDefinition) {
                RouteDefinition routeDefinition = (RouteDefinition)value;
                // add the route
                ((ModelCamelContext)getCamelContext()).addRouteDefinitions(Collections.singletonList(routeDefinition));
                return Response.seeOther(new URI("/routes")).build();
            } else {
                error = "Posted XML is not a route but is of type " + ObjectHelper.className(value);
            }
        } catch (JAXBException e) {
            error = "Failed to parse XML: " + e.getMessage();
        } catch (Exception e) {
            error = "Failed to install route: " + e.getMessage();
        }
        // lets re-render the form
        return Response.ok(new Viewable("create", this)).build();
    }

}
