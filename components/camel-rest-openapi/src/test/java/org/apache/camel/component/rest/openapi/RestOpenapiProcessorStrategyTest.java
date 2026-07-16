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
package org.apache.camel.component.rest.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RestOpenapiProcessorStrategyTest extends ManagedCamelTestSupport {

    private CamelContext camelContext;

    @BeforeEach
    public void createMocks() throws Exception {
        initializeContextForComponent("rest-openapi");
    }

    @Test
    void testMissingOperationId() throws Exception {
        RestOpenapiProcessorStrategy restOpenapiProcessorStrategy = new DefaultRestOpenapiProcessorStrategy();
        ((DefaultRestOpenapiProcessorStrategy) restOpenapiProcessorStrategy).setCamelContext(camelContext);
        restOpenapiProcessorStrategy.setMissingOperation("fail");
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> restOpenapiProcessorStrategy.validateOpenApi(getOpenApi(), null, mock(PlatformHttpConsumerAware.class)));
        assertTrue(ex.getMessage().contains("direct:GENOPID_GET.users"));
        assertTrue(ex.getMessage().contains("direct:GENOPID_GET.user._id_"));

    }

    @Test
    void testMissingOperationIdSetsGeneratedIdOnOperation() throws Exception {
        // Add routes matching the generated GENOPID names so validation passes
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:GENOPID_GET.users").setBody(constant("ok"));
                from("direct:GENOPID_GET.user._id_").setBody(constant("ok"));
            }
        });

        DefaultRestOpenapiProcessorStrategy strategy = new DefaultRestOpenapiProcessorStrategy();
        strategy.setCamelContext(camelContext);
        strategy.setMissingOperation("fail");

        OpenAPI openAPI = getOpenApi();

        // Verify operationIds are initially null
        for (var entry : openAPI.getPaths().entrySet()) {
            for (Operation op : entry.getValue().readOperations()) {
                assertNull(op.getOperationId(),
                        "operationId should be null initially for path: " + entry.getKey());
            }
        }

        // Validation should pass since routes match the generated IDs
        strategy.validateOpenApi(openAPI, null, mock(PlatformHttpConsumerAware.class));

        // Verify generated operationIds were written back to the Operations
        // so that process() can dispatch to the correct direct endpoint
        Operation usersOp = openAPI.getPaths().get("/users").getGet();
        assertEquals("GENOPID_GET.users", usersOp.getOperationId());

        Operation userByIdOp = openAPI.getPaths().get("/user/{id}").getGet();
        assertEquals("GENOPID_GET.user._id_", userByIdOp.getOperationId());
    }

    private OpenAPI getOpenApi() {
        return RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "missing-opid.yaml");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:getUsers").to("mock:getUsers");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext(String componentName) {

        camelContext = new DefaultCamelContext();
        PlatformHttpComponent httpCmpn = mock(PlatformHttpComponent.class);
        camelContext.addComponent("platform-http", httpCmpn);
        return camelContext;
    }

}
