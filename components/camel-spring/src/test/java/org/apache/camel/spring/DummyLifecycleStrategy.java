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
package org.apache.camel.spring;

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Dummy LifecycleStrategy for LifecycleStrategy injection test.
 *
 * @version $Revision$
 *
 */
public class DummyLifecycleStrategy implements LifecycleStrategy {

    public void onContextCreate(CamelContext context) {
        // TODO Auto-generated method stub
        
    }

    public void onEndpointAdd(Endpoint<? extends Exchange> endpoint) {
        // TODO Auto-generated method stub
        
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        // TODO Auto-generated method stub
        
    }

    public void onRoutesAdd(Collection<Route> routes) {
        // TODO Auto-generated method stub
        
    }

    public void onServiceAdd(CamelContext context, Service service) {
        // TODO Auto-generated method stub
        
    }

    public void onContextStart(CamelContext arg0) {
        // TODO Auto-generated method stub
        
    }

}
