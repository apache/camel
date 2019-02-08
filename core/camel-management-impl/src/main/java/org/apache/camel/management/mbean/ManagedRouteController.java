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
package org.apache.camel.management.mbean;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.mbean.ManagedRouteControllerMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteController;

public class ManagedRouteController implements ManagedRouteControllerMBean {
    private final ModelCamelContext context;

    public ManagedRouteController(ModelCamelContext context) {
        this.context = context;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    @Override
    public Collection<String> getControlledRoutes() {
        RouteController controller = context.getRouteController();

        if (controller != null) {
            return controller.getControlledRoutes().stream()
                .map(Route::getId)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
