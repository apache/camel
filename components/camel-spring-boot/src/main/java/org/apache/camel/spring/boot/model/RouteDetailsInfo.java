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
package org.apache.camel.spring.boot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;

/**
 * Container for exposing {@link org.apache.camel.Route} information
 * with route details as JSON. Route details are retrieved from JMX.
 */
public class RouteDetailsInfo extends RouteInfo {

    @JsonProperty("details")
    private RouteDetails routeDetails;

    public RouteDetailsInfo(final CamelContext camelContext, final Route route) {
        super(route);

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            this.routeDetails = new RouteDetails(camelContext.getManagedRoute(route.getId(), ManagedRouteMBean.class));
        }
    }

}
