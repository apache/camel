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
package org.apache.camel.component.torchserve;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

class InferenceTest extends TorchServeTestSupport {

    private static final String TEST_MODEL = "squeezenet1_1";
    private static final String TEST_MODEL_VERSION = "1.0";
    private static final String TEST_DATA = "src/test/resources/data/kitten.jpg";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        var context = super.createCamelContext();
        var component = context.getComponent("torchserve", TorchServeComponent.class);
        var configuration = component.getConfiguration();
        configuration.setInferenceAddress(mockServer.baseUrl());
        return context;
    }

    @Test
    void testPing() throws Exception {
        mockServer.stubFor(get("/ping")
                .willReturn(okJson("{ \"status\": \"Healthy\" }")));
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().constant("Healthy");

        template.sendBody("direct:ping", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testPredictions() throws Exception {
        mockServer.stubFor(post("/predictions/" + TEST_MODEL)
                .willReturn(okJson("{ \"cat\": 1.0 }")));
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived()
                .body(Map.class)
                .isEqualTo(Map.of("cat", 1.0));

        var body = Files.readAllBytes(Path.of(TEST_DATA));
        template.sendBody("direct:predictions", body);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testPredictions_headers() throws Exception {
        mockServer.stubFor(post("/predictions/" + TEST_MODEL)
                .willReturn(okJson("{ \"cat\": 1.0 }")));
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived()
                .body(Map.class)
                .isEqualTo(Map.of("cat", 1.0));

        var body = Files.readAllBytes(Path.of(TEST_DATA));
        template.send("direct:predictions_headers", exchange -> {
            exchange.getMessage().setHeader(TorchServeConstants.MODEL_NAME, TEST_MODEL);
            exchange.getMessage().setBody(body);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testPredictions_version() throws Exception {
        mockServer.stubFor(post("/predictions/" + TEST_MODEL + "/" + TEST_MODEL_VERSION)
                .willReturn(okJson("{ \"cat\": 1.0 }")));
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived()
                .body(Map.class)
                .isEqualTo(Map.of("cat", 1.0));

        var body = Files.readAllBytes(Path.of(TEST_DATA));
        template.sendBody("direct:predictions_version", body);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testPredictions_versionHeaders() throws Exception {
        mockServer.stubFor(post("/predictions/" + TEST_MODEL + "/" + TEST_MODEL_VERSION)
                .willReturn(okJson("{ \"cat\": 1.0 }")));
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived()
                .body(Map.class)
                .isEqualTo(Map.of("cat", 1.0));

        var body = Files.readAllBytes(Path.of(TEST_DATA));
        template.send("direct:predictions_headers", exchange -> {
            exchange.getMessage().setHeader(TorchServeConstants.MODEL_NAME, TEST_MODEL);
            exchange.getMessage().setHeader(TorchServeConstants.MODEL_VERSION, TEST_MODEL_VERSION);
            exchange.getMessage().setBody(body);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ping")
                        .to("torchserve:inference/ping")
                        .to("mock:result");
                from("direct:predictions")
                        .toF("torchserve:inference/predictions?modelName=%s", TEST_MODEL)
                        .to("mock:result");
                from("direct:predictions_version")
                        .toF("torchserve:inference/predictions?modelName=%s&modelVersion=%s", TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:predictions_headers")
                        .to("torchserve:inference/predictions")
                        .to("mock:result");
            }
        };
    }
}
