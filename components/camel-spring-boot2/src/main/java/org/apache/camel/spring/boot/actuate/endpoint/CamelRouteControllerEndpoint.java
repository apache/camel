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
package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link Endpoint} to expose {@link RouteController} information.
 */
@ConfigurationProperties(prefix = "endpoints." + CamelRouteControllerEndpoint.ENDPOINT_ID)
public class CamelRouteControllerEndpoint extends AbstractEndpoint<List<String>> {

    public static final String ENDPOINT_ID = "camelroutecontroller";

    private CamelContext camelContext;

    public CamelRouteControllerEndpoint(CamelContext camelContext) {
        super(ENDPOINT_ID);
        this.camelContext = camelContext;
        // is enabled by default
        this.setEnabled(true);
    }

    @Override
    public List<String> invoke() {
        RouteController controller = camelContext.getRouteController();

        if (controller != null) {
            return controller.getControlledRoutes().stream()
                .map(Route::getId)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

}
