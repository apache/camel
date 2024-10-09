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
package org.apache.camel.component.torchserve.it;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.torchserve.TorchServeConstants;
import org.apache.camel.component.torchserve.client.model.ModelList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ManagementIT extends TorchServeITSupport {

    private static final String TEST_MODEL = "squeezenet1_1";
    private static final String TEST_MODEL_VERSION = "1.0";
    private static final String ADDED_MODEL_URL = "https://torchserve.pytorch.org/mar_files/mnist_v2.mar";
    private static final String ADDED_MODEL = "mnist_v2";
    private static final String ADDED_MODEL_VERSION = "2.0";

    @BeforeEach
    void resetMock() {
        var mock = getMockEndpoint("mock:result");
        mock.reset();
    }

    @Test
    void testRegister() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().body().contains("registered");

        template.sendBody("direct:register", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Nested
    class AfterRegisteringModel {

        @BeforeEach
        void registerModel() {
            try {
                template.sendBody("direct:register", null);
            } catch (CamelExecutionException e) {
                // Ignore if the model is already registered
            }
            var mock = getMockEndpoint("mock:result");
            mock.reset();
        }

        @Test
        void testUnregister() throws Exception {
            var mock = getMockEndpoint("mock:result");
            mock.expectedBodyReceived().body().contains("unregistered");

            template.sendBody("direct:unregister", null);

            mock.await(1, TimeUnit.SECONDS);
            mock.assertIsSatisfied();
        }

        @Test
        void testUnregister_headers() throws Exception {
            var mock = getMockEndpoint("mock:result");
            mock.expectedBodyReceived().body().contains("unregistered");

            template.send("direct:unregister_headers",
                    exchange -> exchange.getMessage().setHeader(TorchServeConstants.MODEL_NAME, ADDED_MODEL));

            mock.await(1, TimeUnit.SECONDS);
            mock.assertIsSatisfied();
        }

        @Test
        void testUnregister_version() throws Exception {
            var mock = getMockEndpoint("mock:result");
            mock.expectedBodyReceived().body().contains("unregistered");

            template.sendBody("direct:unregister_version", null);

            mock.await(1, TimeUnit.SECONDS);
            mock.assertIsSatisfied();
        }

        @Test
        void testUnregister_versionHeaders() throws Exception {
            var mock = getMockEndpoint("mock:result");
            mock.expectedBodyReceived().body().contains("unregistered");

            template.send("direct:unregister_headers", exchange -> {
                exchange.getMessage().setHeader(TorchServeConstants.MODEL_NAME, ADDED_MODEL);
                exchange.getMessage().setHeader(TorchServeConstants.MODEL_VERSION, ADDED_MODEL_VERSION);
            });

            mock.await(1, TimeUnit.SECONDS);
            mock.assertIsSatisfied();
        }

        @Nested
        class BeforeUnregisteringModel {

            @AfterEach
            void unregisterModel() {
                template.sendBody("direct:unregister", null);

                var mock = getMockEndpoint("mock:result");
                mock.reset();
            }

            @Test
            void testScaleWorker() throws Exception {
                var mock = getMockEndpoint("mock:result");
                mock.expectedBodyReceived().body().contains("processing");

                template.sendBody("direct:scale-worker", null);

                mock.await(1, TimeUnit.SECONDS);
                mock.assertIsSatisfied();
            }

            @Test
            void testScaleWorker_headers() throws Exception {
                var mock = getMockEndpoint("mock:result");
                mock.expectedBodyReceived().body().contains("processing");

                template.send("direct:scale-worker_headers",
                        exchange -> exchange.getMessage().setHeader(TorchServeConstants.MODEL_NAME, ADDED_MODEL));

                mock.await(1, TimeUnit.SECONDS);
                mock.assertIsSatisfied();
            }
        }
    }

    @Test
    void testDescribe() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:list", null);
        mock.await(1, TimeUnit.SECONDS);

        mock.assertIsSatisfied();
    }

    @Test
    void testDescribe_version() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:list", null);
        mock.await(1, TimeUnit.SECONDS);

        mock.assertIsSatisfied();
    }

    @Test
    void testList() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().body(ModelList.class);

        template.sendBody("direct:list", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testSetDefault() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().body().contains("Default vesion succsesfully updated");

        template.sendBody("direct:set-default", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testSetDefault_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().body().contains("Default vesion succsesfully updated");

        template.send("direct:set-default_headers", exchange -> {
            exchange.getMessage().setHeader(TorchServeConstants.MODEL_NAME, TEST_MODEL);
            exchange.getMessage().setHeader(TorchServeConstants.MODEL_VERSION, TEST_MODEL_VERSION);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:register")
                        .toF("torchserve:management/register?url=%s", ADDED_MODEL_URL)
                        .to("mock:result");
                from("direct:scale-worker")
                        .toF("torchserve:management/scale-worker?modelName=%s", ADDED_MODEL)
                        .to("mock:result");
                from("direct:scale-worker_headers")
                        .to("torchserve:management/scale-worker")
                        .to("mock:result");
                from("direct:describe")
                        .to("torchserve:management/describe")
                        .to("mock:result");
                from("direct:unregister")
                        .toF("torchserve:management/unregister?modelName=%s", ADDED_MODEL)
                        .to("mock:result");
                from("direct:unregister_version")
                        .toF("torchserve:management/unregister?modelName=%s&modelVersion=%s", ADDED_MODEL, ADDED_MODEL_VERSION)
                        .to("mock:result");
                from("direct:unregister_headers")
                        .to("torchserve:management/unregister")
                        .to("mock:result");
                from("direct:list")
                        .to("torchserve:management/list")
                        .to("mock:result");
                from("direct:set-default")
                        .toF("torchserve:management/set-default?modelName=%s&modelVersion=%s", TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:set-default_headers")
                        .to("torchserve:management/set-default")
                        .to("mock:result");
            }
        };
    }
}
