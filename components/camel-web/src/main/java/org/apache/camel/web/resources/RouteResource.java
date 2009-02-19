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
package org.apache.camel.web.resources;

import org.apache.camel.model.RouteType;
import org.apache.camel.view.RouteDotGenerator;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

/**
 * A single Camel Route which is used to implement one or more
 *  <a href="http://camel.apache.org/enterprise-integration-patterns.html">Enterprise Integration Paterns</a>
 *
 * @version $Revision: 1.1 $
 */
public class RouteResource extends CamelChildResourceSupport {
    private RouteType route;

    public RouteResource(RoutesResource routesResource, RouteType route) {
        super(routesResource.getContextResource());
        this.route = route;
    }

    /**
     * Returns the XML or JSON representation of this route
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public RouteType getRoute() {
        return route;
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

}
