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
package org.apache.camel.impl.console;

import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.util.TimeUtils;

import java.util.List;
import java.util.Map;

@DevConsole("route")
public class RouteDevConsole extends AbstractDevConsole {

    public RouteDevConsole() {
        super("camel", "route", "Route", "Route information");
    }

    @Override
    protected Object doCall(MediaType mediaType, Map<String, Object> options) {
        // only text is supported
        StringBuilder sb = new StringBuilder();

        ManagedCamelContext mcc = getCamelContext().getExtension(ManagedCamelContext.class);
        if (mcc != null) {
            List<Route> routes = getCamelContext().getRoutes();
            routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
            for (Route route : routes) {
                ManagedRouteMBean mrb = mcc.getManagedRoute(route.getRouteId());
                if (mrb != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(String.format("    Id: %s", mrb.getRouteId()));
                    if (mrb.getSourceLocation() != null) {
                        sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
                    }
                    sb.append(String.format("\n    State: %s", mrb.getState()));
                    sb.append(String.format("\n    Uptime: %s", mrb.getUptime()));
                    sb.append(String.format("\n    Total: %s", mrb.getExchangesTotal()));
                    sb.append(String.format("\n    Failed: %s", mrb.getExchangesFailed()));
                    sb.append(String.format("\n    Inflight: %s", mrb.getExchangesInflight()));
                    sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mrb.getMeanProcessingTime())));
                    sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mrb.getMaxProcessingTime())));
                    sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mrb.getMinProcessingTime())));
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

}
