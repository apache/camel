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
package org.apache.camel.component.routebox;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The routebox component allows to send/receive messages between Camel routes in a black box way.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "routebox", title = "RouteBox", syntax = "routebox:routeboxName", consumerClass = RouteboxConsumer.class, label = "eventbus")
public abstract class RouteboxEndpoint extends DefaultEndpoint {

    @UriParam
    RouteboxConfiguration config;

    public RouteboxEndpoint() {
    }
    
    @Deprecated
    public RouteboxEndpoint(String endpointUri) {
        super(endpointUri);
    }
    
    @Deprecated
    public RouteboxEndpoint(String endpointUri, CamelContext camelContext) {
        super(endpointUri, camelContext);
    }

    public RouteboxEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public RouteboxEndpoint(String endpointUri, Component component, RouteboxConfiguration config) {
        super(endpointUri, component);
        this.config = config;
    }  

    public RouteboxConfiguration getConfig() {
        return config;
    }

    public void setConfig(RouteboxConfiguration config) {
        this.config = config;
    }
    
}
