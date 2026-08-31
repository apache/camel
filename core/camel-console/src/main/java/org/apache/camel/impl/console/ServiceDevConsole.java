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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "service", displayName = "Services", description = "Services used for network communication with clients")
public class ServiceDevConsole extends AbstractDevConsole {

    public record ServiceEntry(
            @Metadata(description = "The Camel component") String component,
            @Metadata(description = "The direction (in or out)") String direction,
            @Metadata(description = "Whether the service is hosted in this Camel application") boolean hosted,
            @Metadata(description = "The protocol the service is using (only present when known)") String protocol,
            @Metadata(description = "The remote address of the service (only present when known)") String serviceUrl,
            @Metadata(description = "The endpoint URI") String endpointUri,
            @Metadata(description = "The route ID (only present when known)") String routeId,
            @Metadata(description = "Usage of the endpoint service") long hits,
            @Metadata(description = "Additional metadata relevant to the service (only present when available)") Map<String, String> metadata) {
    }

    public record Response(@Metadata(description = "The services") List<ServiceEntry> services) {
    }

    public ServiceDevConsole() {
        super("camel", "service", "Services", "Services used for network communication with clients");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        EndpointServiceRegistry esr = getCamelContext().getCamelContextExtension().getEndpointServiceRegistry();
        for (EndpointServiceRegistry.EndpointService es : esr.listAllEndpointServices()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(String.format("%n    Component: %s", es.getComponent()));
            sb.append(String.format("%n    Direction: %s", es.getDirection()));
            sb.append(String.format("%n    Hosted: %b", es.isHostedService()));
            sb.append(String.format("%n    Protocol: %s", es.getServiceProtocol()));
            sb.append(String.format("%n    Service: %s", es.getServiceUrl()));
            sb.append(String.format("%n    Endpoint: %s", URISupport.sanitizeUri(es.getServiceUrl())));
            if (es.getRouteId() != null) {
                sb.append(String.format("%n    Route Id: %s", es.getRouteId()));
            }
            sb.append(String.format("%n    Total Messages: %d", es.getHits()));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<ServiceEntry> list = new ArrayList<>();

        EndpointServiceRegistry esr = getCamelContext().getCamelContextExtension().getEndpointServiceRegistry();
        for (EndpointServiceRegistry.EndpointService es : esr.listAllEndpointServices()) {
            list.add(new ServiceEntry(
                    es.getComponent(), es.getDirection(), es.isHostedService(), es.getServiceProtocol(),
                    es.getServiceUrl(), es.getEndpointUri(), es.getRouteId(), es.getHits(), es.getServiceMetadata()));
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

}
