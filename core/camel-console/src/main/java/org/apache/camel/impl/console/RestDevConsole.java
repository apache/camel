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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "rest", displayName = "Rest", description = "Rest DSL Registry information")
public class RestDevConsole extends AbstractDevConsole {

    public record RestEntry(
            @Metadata(description = "The absolute URL to the REST service") String url,
            @Metadata(description = "The HTTP method") String method,
            @Metadata(description = "Whether the REST service is contract-first") boolean contractFirst,
            @Metadata(description = "Whether this is the API contract specification (i.e. api-doc)") boolean specification,
            @Metadata(description = "The REST service state") String state,
            @Metadata(description = "The route ID (only present when known)") String routeId,
            @Metadata(description = "The OpenAPI operation ID (only present for contract-first routes)") String operationId,
            @Metadata(description = "The OpenAPI specification URI (only present for contract-first routes)") String specificationUri,
            @Metadata(description = "The accepted media types (only present when known)") String consumes,
            @Metadata(description = "The returned media types (only present when known)") String produces,
            @Metadata(description = "The input binding class name (only present when known)") String inType,
            @Metadata(description = "The output binding class name (only present when known)") String outType,
            @Metadata(description = "The REST service description (only present when configured)") String description,
            @Metadata(description = "Usage of the REST service (only present when greater than zero)") Long hits) {
    }

    public record Response(
            @Metadata(description = "The REST services (only present when a REST registry is available)") List<RestEntry> rests) {
    }

    public RestDevConsole() {
        super("camel", "rest", "Rest", "Rest DSL Registry information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        // camel-rest is optional; look up lazily so rest-openapi routes (which register
        // after route warm-up via afterPropertiesConfigured) are visible on first call
        RestRegistry rr = PluginHelper.getRestRegistry(getCamelContext());
        if (rr != null) {
            for (RestRegistry.RestService rs : rr.listAllRestServices()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(String.format("%n    Url: %s", rs.getUrl()));
                sb.append(String.format("%n    Method: %s", rs.getMethod()));
                sb.append(String.format("%n    Contract First: %s", rs.isContractFirst()));
                sb.append(String.format("%n    Specification: %s", rs.isSpecification()));
                sb.append(String.format("%n    State: %s", rs.getState()));
                if (rs.getRouteId() != null) {
                    sb.append(String.format("%n    Route Id: %s", rs.getRouteId()));
                }
                if (rs.getOperationId() != null) {
                    sb.append(String.format("%n    Operation Id: %s", rs.getOperationId()));
                }
                if (rs.getSpecificationUri() != null) {
                    sb.append(String.format("%n    Specification: %s", rs.getSpecificationUri()));
                }
                if (rs.getConsumes() != null) {
                    sb.append(String.format("%n    Consumes: %s", rs.getConsumes()));
                }
                if (rs.getProduces() != null) {
                    sb.append(String.format("%n    Produces: %s", rs.getProduces()));
                }
                if (rs.getInType() != null) {
                    sb.append(String.format("%n    In Type: %s", rs.getInType()));
                }
                if (rs.getOutType() != null) {
                    sb.append(String.format("%n    Out Type: %s", rs.getOutType()));
                }
                if (rs.getDescription() != null) {
                    sb.append(String.format("%n    Description: %s", rs.getDescription()));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<RestEntry> rests = null;

        RestRegistry rr = PluginHelper.getRestRegistry(getCamelContext());
        if (rr != null) {
            rests = new ArrayList<>();
            for (RestRegistry.RestService rs : rr.listAllRestServices()) {
                long hits = rs.getHits();
                rests.add(new RestEntry(
                        rs.getUrl(), rs.getMethod(), rs.isContractFirst(), rs.isSpecification(), rs.getState(),
                        rs.getRouteId(), rs.getOperationId(), rs.getSpecificationUri(), rs.getConsumes(),
                        rs.getProduces(), rs.getInType(), rs.getOutType(), rs.getDescription(),
                        hits > 0 ? hits : null));
            }
        }

        Response response = new Response(rests);
        return JsonRecordSupport.toJsonObject(response);
    }

}
