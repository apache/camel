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
package org.apache.camel.impl;

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Default implementation of the lifecycle strategy.
 */
public class DefaultLifecycleStrategy implements LifecycleStrategy {

    public void onContextStart(CamelContext context) {
        // do nothing
    }

    public void onEndpointAdd(Endpoint<? extends Exchange> endpoint) {
        // do nothing
    }

    public void onServiceAdd(CamelContext context, Service service) {
        // do nothing
    }

    public void onRoutesAdd(Collection<Route> routes) {
        // do nothing
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        // do nothing
    }
}
