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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.EndpointServiceRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonObject;

@DevConsole(
        name = "service",
        displayName = "Services",
        description = "Services used for network communication with clients")
public class ServiceDevConsole extends AbstractDevConsole {

    public ServiceDevConsole() {
        super("camel", "service", "Services", "Services used for network communication with clients");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        EndpointServiceRegistry esr =
                getCamelContext().getCamelContextExtension().getEndpointServiceRegistry();
        for (EndpointServiceRegistry.EndpointService es : esr.listAllEndpointServices()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(String.format("\n    Component: %s", es.getComponent()));
            sb.append(String.format("\n    Direction: %s", es.getDirection()));
            sb.append(String.format("\n    Hosted: %b", es.isHostedService()));
            sb.append(String.format("\n    Protocol: %s", es.getServiceProtocol()));
            sb.append(String.format("\n    Service: %s", es.getServiceUrl()));
            sb.append(String.format("\n    Endpoint: %s", URISupport.sanitizeUri(es.getServiceUrl())));
            if (es.getRouteId() != null) {
                sb.append(String.format("\n    Route Id: %s", es.getRouteId()));
            }
            sb.append(String.format("\n    Total Messages: %d", es.getHits()));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<JsonObject> list = new ArrayList<>();
        root.put("services", list);

        EndpointServiceRegistry esr =
                getCamelContext().getCamelContextExtension().getEndpointServiceRegistry();
        for (EndpointServiceRegistry.EndpointService es : esr.listAllEndpointServices()) {
            JsonObject jo = new JsonObject();
            jo.put("component", es.getComponent());
            jo.put("direction", es.getDirection());
            jo.put("hosted", es.isHostedService());
            jo.put("protocol", es.getServiceProtocol());
            jo.put("serviceUrl", es.getServiceUrl());
            jo.put("endpointUri", es.getEndpointUri());
            if (es.getRouteId() != null) {
                jo.put("routeId", es.getRouteId());
            }
            jo.put("hits", es.getHits());
            var map = es.getServiceMetadata();
            if (map != null) {
                jo.put("metadata", map);
            }
            list.add(jo);
        }

        return root;
    }
}
