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
package org.apache.camel.management;

import java.io.IOException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "Managed Route", currencyTimeLimit = 15)
public class ManagedRoute extends PerformanceCounter {

    public static final String VALUE_UNKNOWN = "Unknown";
    private Route<? extends Exchange> route;
    private String description;

    ManagedRoute(Route<? extends Exchange> route) {
        this.route = route;
        this.description = route.toString();
    }

    public Route<? extends Exchange> getRoute() {
        return route;
    }

    @ManagedAttribute(description = "Route Endpoint Uri")
    public String getEndpointUri() {
        Endpoint<? extends Exchange> ep = route.getEndpoint();
        return ep != null ? ep.getEndpointUri() : VALUE_UNKNOWN;
    }

    @ManagedAttribute(description = "Route description")
    public String getDescription() {
        return description;
    }

    @ManagedOperation(description = "Start Route")
    public void start() throws IOException {
        throw new IOException("Not supported");
    }

    @ManagedOperation(description = "Stop Route")
    public void stop() throws IOException {
        throw new IOException("Not supported");
    }
}
