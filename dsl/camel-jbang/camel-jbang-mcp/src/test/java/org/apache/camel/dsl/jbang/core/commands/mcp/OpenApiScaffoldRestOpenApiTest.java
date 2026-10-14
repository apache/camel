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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.component.rest.openapi.RestOpenApiComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds what camel_openapi_scaffold generates for the petstore spec with the real camel-rest-openapi component. The
 * HTTP server is replaced by an engine that hands the requests to the rest-openapi consumer in-process.
 */
class OpenApiScaffoldRestOpenApiTest {

    private static String petstore;

    private final OpenApiTools tools = new OpenApiTools();
    private final AtomicReference<Processor> consumerProcessor = new AtomicReference<>();

    @TempDir
    Path dir;

    @BeforeAll
    static void loadSpec() throws Exception {
        try (InputStream is = OpenApiScaffoldRestOpenApiTest.class.getResourceAsStream("/petstore.yaml")) {
            petstore = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void scaffoldedRouteOfAnOperationWithoutOperationIdIsCalled() throws Exception {
        OpenApiTools.ScaffoldResult scaffold = tools.camel_openapi_scaffold(petstore, "petstore.yaml", null);

        // in the default 'fail' mode the context only starts when every operation of the spec has its route
        try (CamelContext context = start(scaffold.yaml(), null)) {
            Exchange answer = call(context, "/v1/pets/42");

            assertThat(answer.getException()).isNull();
            assertThat(answer.getMessage().getBody(String.class)).isEqualTo("TODO: implement GENOPID_GET.pets._petId_");
        }
    }

    @Test
    void scaffoldedMockFileIsServed() throws Exception {
        OpenApiTools.ScaffoldResult scaffold = tools.camel_openapi_scaffold(petstore, "petstore.yaml", "mock");

        assertThat(scaffold.mockFiles())
                .extracting(OpenApiTools.MockFileInfo::filePath)
                .containsExactly("camel-mock/pets.json", "camel-mock/pets/{petId}.json");
        Path file = dir.resolve(scaffold.mockFiles().get(1).filePath().replace("{petId}", "42"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"id\": 42, \"name\": \"Tom\"}");

        // keep only the rest block, so that no operation has a route
        String restOnly = scaffold.yaml().substring(0, scaffold.yaml().indexOf("- route:"));
        try (CamelContext context = start(restOnly, "file:" + dir.toAbsolutePath() + "/camel-mock/**")) {
            Exchange answer = call(context, "/v1/pets/42");

            assertThat(answer.getException()).isNull();
            assertThat(answer.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(200);
            assertThat(answer.getMessage().getBody(String.class)).isEqualToIgnoringNewLines("{\"id\": 42, \"name\": \"Tom\"}");
        }
    }

    private CamelContext start(String yaml, String mockIncludePattern) throws Exception {
        CamelContext context = new DefaultCamelContext();
        PlatformHttpEngine engine = (endpoint, processor) -> {
            consumerProcessor.set(processor);
            return new InVmConsumer(endpoint, processor);
        };
        context.getRegistry().bind(PlatformHttpConstants.PLATFORM_HTTP_ENGINE_NAME, engine);
        if (mockIncludePattern != null) {
            context.getComponent("rest-openapi", RestOpenApiComponent.class).setMockIncludePattern(mockIncludePattern);
        }
        PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("scaffold.camel.yaml", yaml));
        context.start();
        return context;
    }

    private Exchange call(CamelContext context, String path) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, path);
        consumerProcessor.get().process(exchange);
        return exchange;
    }

    private static final class InVmConsumer extends DefaultConsumer implements PlatformHttpConsumer {

        InVmConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        public PlatformHttpEndpoint getEndpoint() {
            return (PlatformHttpEndpoint) super.getEndpoint();
        }
    }
}
