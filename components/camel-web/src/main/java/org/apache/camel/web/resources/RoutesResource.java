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

import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.RoutesType;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class RoutesResource extends CamelChildResourceSupport {

    public RoutesResource(CamelContextResource contextResource) {
        super(contextResource);
    }

    /**
     * Returns the routes currently active within this context
     *
     * @return
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public RoutesType getRouteDefinitions() {
        RoutesType answer = new RoutesType();
        CamelContext camelContext = getCamelContext();
        if (camelContext != null) {
            List<RouteType> list = camelContext.getRouteDefinitions();
            answer.setRoutes(list);
        }
        return answer;
    }

    /**
      * Looks up an individual route
      */
     @Path("{id}")
     public RouteResource getEndpoint(@PathParam("id") String id) {
        List<RouteType> list = getRoutes();
        for (RouteType routeType : list) {
            if (routeType.getId().equals(id)) {
                return new RouteResource(this, routeType);
            }
        }
        return null;
     }


    // Properties
    //-------------------------------------------------------------------------
    public List<RouteType> getRoutes() {
        return getRouteDefinitions().getRoutes();
    }
}