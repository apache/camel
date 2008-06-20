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
 * A helper class for folks writing delegate listener strategies
 *
 * @version $Revision$
 */
public class DelegateLifecycleStrategy implements LifecycleStrategy {
    private final LifecycleStrategy delegate;

    public DelegateLifecycleStrategy(LifecycleStrategy delegate) {
        this.delegate = delegate;
    }

    public void onContextStart(CamelContext context) {
        delegate.onContextStart(context);
    }

    public void onEndpointAdd(Endpoint<? extends Exchange> endpoint) {
        delegate.onEndpointAdd(endpoint);
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        delegate.onRouteContextCreate(routeContext);
    }

    public void onRoutesAdd(Collection<Route> routes) {
        delegate.onRoutesAdd(routes);
    }

    public void onServiceAdd(CamelContext context, Service service) {
        delegate.onServiceAdd(context, service);
    }
}
