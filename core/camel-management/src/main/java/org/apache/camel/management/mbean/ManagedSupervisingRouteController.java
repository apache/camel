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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedSupervisingRouteControllerMBean;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.backoff.BackOffTimer;

@ManagedResource(description = "Managed SupervisingRouteController")
public class ManagedSupervisingRouteController extends ManagedService implements ManagedSupervisingRouteControllerMBean {

    private final SupervisingRouteController controller;

    public ManagedSupervisingRouteController(CamelContext context, SupervisingRouteController controller) {
        super(context, controller);
        this.controller = controller;
    }

    public SupervisingRouteController getRouteController() {
        return controller;
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

    @Override
    public int getNumberOfExhaustedRoutes() {
        return controller.getExhaustedRoutes().size();
    }

    @Override
    public Collection<String> getControlledRoutes() {
        if (controller != null) {
            return controller.getControlledRoutes().stream()
                    .map(Route::getId)
                    .toList();
        }

        return Collections.emptyList();
    }

    @Override
    public String getRouteStartupLoggingLevel() {
        if (controller != null) {
            return controller.getLoggingLevel().name();
        } else {
            return null;
        }
    }

    @Override
    public Collection<String> getRestartingRoutes() {
        if (controller != null) {
            return controller.getRestartingRoutes().stream()
                    .map(Route::getId)
                    .toList();
        }

        return Collections.emptyList();
    }

    @Override
    public Collection<String> getExhaustedRoutes() {
        if (controller != null) {
            return controller.getExhaustedRoutes().stream()
                    .map(Route::getId)
                    .toList();
        }

        return Collections.emptyList();
    }

    @Override
    public TabularData routeStatus(boolean exhausted, boolean restarting, boolean includeStacktrace) {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.supervisingRouteControllerRouteStatusTabularType());

            int index = 0;
            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(controller.getControlledRoutes());
            if (exhausted) {
                routes.addAll(controller.getExhaustedRoutes());
            }
            if (restarting) {
                routes.addAll(controller.getRestartingRoutes());
            }

            for (Route route : routes) {
                CompositeType ct = CamelOpenMBeanTypes.supervisingRouteControllerRouteStatusCompositeType();

                String routeId = route.getRouteId();
                String status = controller.getRouteStatus(routeId).name();
                BackOffTimer.Task state = controller.getRestartingRouteState(routeId);
                String supervising = state != null ? state.getStatus().name() : "";
                long attempts = state != null ? state.getCurrentAttempts() : 0;
                String elapsed = "";
                String last = "";
                // we can only track elapsed/time for active supervised routes
                long time = state != null && BackOffTimer.Task.Status.Active == state.getStatus()
                        ? state.getFirstAttemptTime() : 0;
                if (time > 0) {
                    long delta = System.currentTimeMillis() - time;
                    elapsed = TimeUtils.printDuration(delta, true);
                }
                time = state != null && BackOffTimer.Task.Status.Active == state.getStatus() ? state.getLastAttemptTime() : 0;
                if (time > 0) {
                    long delta = System.currentTimeMillis() - time;
                    last = TimeUtils.printDuration(delta, true);
                }
                String error = "";
                String stacktrace = "";
                Throwable cause = controller.getRestartException(routeId);
                if (cause != null) {
                    error = cause.getMessage();
                    if (includeStacktrace) {
                        StringWriter writer = new StringWriter();
                        cause.printStackTrace(new PrintWriter(writer));
                        writer.flush();
                        stacktrace = writer.toString();
                    }
                }

                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] {
                                "index", "routeId", "status", "supervising", "attempts", "elapsed", "last", "error",
                                "stacktrace" },
                        new Object[] { index, routeId, status, supervising, attempts, elapsed, last, error, stacktrace });
                answer.put(data);

                // use a counter as the single index in the TabularData as we do not want a multi-value index
                index++;
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
