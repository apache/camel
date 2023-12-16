/*
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
package org.apache.camel.impl.health;

import java.util.Map;

import org.apache.camel.Ordered;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.spi.RouteController;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Readiness {@link org.apache.camel.health.HealthCheck} for route controller.
 */
@org.apache.camel.spi.annotations.HealthCheck("route-controller-check")
public class RouteControllerHealthCheck extends AbstractHealthCheck {

    public RouteControllerHealthCheck() {
        super("camel", "route-controller");
    }

    @Override
    public int getOrder() {
        // controller should be early
        return Ordered.HIGHEST + 1000;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        boolean up = false;

        RouteController rc = getCamelContext().getRouteController();
        if (rc != null) {
            // should only be up if there are no unhealthy routes
            up = !rc.isUnhealthyRoutes();
        }

        if (up) {
            builder.up();
        } else {
            builder.detail("route.controller", "Starting routes in progress");
            builder.down();
        }
    }

}
