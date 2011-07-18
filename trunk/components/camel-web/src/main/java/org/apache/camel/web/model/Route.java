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
package org.apache.camel.web.model;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.model.DescriptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.web.util.JMXRouteStatistics;
import org.apache.camel.web.util.RouteStatistics;

/**
 * Represents a route.
 * <p/>
 * We need this model to link the RouteDefinition with a CamelContext
 */
public class Route {
    private CamelContext camelContext;
    private RouteDefinition route;
    private final RouteStatistics statistics = new JMXRouteStatistics();

    public Route() {
    }

    public Route(CamelContext camelContext, RouteDefinition route) {
        this.camelContext = camelContext;
        this.route = route;
    }

    public String getId() {
        return route.getId();
    }

    public String idOrCreate() {
        return route.idOrCreate(camelContext.getNodeIdFactory());
    }

    public DescriptionDefinition getDescription() {
        return route.getDescription();
    }

    public String getDescriptionText() {
        DescriptionDefinition definition = getDescription();
        return (definition != null) ? definition.getText() : "";
    }

    public ServiceStatus getStatus() {
        return route.getStatus(camelContext);
    }

    public boolean isStartable() {
        return route.isStartable(camelContext);
    }

    public boolean isStoppable() {
        return route.isStoppable(camelContext);
    }
    
    public Object getStatistic(String attribute) {
        Object answer = statistics.getRouteStatistic(camelContext, route.getId(), attribute);
        if (answer == null) {
            // we don't want null on web pages so we return an empty string
            return "";
        }
        return answer;
    }
}
