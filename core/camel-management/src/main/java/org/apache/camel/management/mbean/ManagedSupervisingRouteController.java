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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedSupervisingRouteControllerMBean;
import org.apache.camel.spi.SupervisingRouteController;

@ManagedResource(description = "Managed SupervisingRouteController")
public class ManagedSupervisingRouteController extends ManagedService implements ManagedSupervisingRouteControllerMBean {

    private final SupervisingRouteController controller;

    public ManagedSupervisingRouteController(CamelContext context, SupervisingRouteController controller) {
        super(context, controller);
        this.controller = controller;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getThreadPoolSize() {
        return controller.getThreadPoolSize();
    }

    @Override
    public long getInitialDelay() {
        return controller.getInitialDelay();
    }

    @Override
    public long getBackOffDelay() {
        return controller.getBackOffDelay();
    }

    @Override
    public long getBackOffMaxDelay() {
        return controller.getBackOffMaxDelay();
    }

    @Override
    public long getBackOffMaxElapsedTime() {
        return controller.getBackOffMaxElapsedTime();
    }

    @Override
    public long getBackOffMaxAttempts() {
        return controller.getBackOffMaxAttempts();
    }

    @Override
    public double getBackOffMultiplier() {
        return controller.getBackOffMultiplier();
    }

    @Override
    public String getIncludeRoutes() {
        return controller.getIncludeRoutes();
    }

    @Override
    public String getExcludeRoutes() {
        return controller.getExcludeRoutes();
    }

    @Override
    public int getNumberOfControlledRoutes() {
        return controller.getControlledRoutes().size();
    }

    @Override
    public int getNumberOfRestartingRoutes() {
        return controller.getRestartingRoutes().size();
    }
}
