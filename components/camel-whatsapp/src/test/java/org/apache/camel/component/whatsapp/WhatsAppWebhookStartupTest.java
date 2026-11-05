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
package org.apache.camel.component.whatsapp;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsAppWebhookStartupTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    void webhookRouteWithoutSecretFailsToStart() throws Exception {
        addWebhookRoute("");

        assertThatThrownBy(context::start)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhookSecret")
                .hasMessageContaining("webhookVerification=false");
    }

    @Test
    void webhookRouteWithEmptySecretFailsToStart() throws Exception {
        addWebhookRoute("&webhookSecret=");

        assertThatThrownBy(context::start)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhookSecret");
    }

    @Test
    void webhookRouteWithSecretStarts() throws Exception {
        addWebhookRoute("&webhookSecret=my-app-secret");

        context.start();

        assertThat(context.getRouteController().getRouteStatus("webhook")).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void webhookRouteWithVerificationDisabledStartsWithoutSecret() throws Exception {
        addWebhookRoute("&webhookVerification=false");

        context.start();

        assertThat(context.getRouteController().getRouteStatus("webhook")).isEqualTo(ServiceStatus.Started);
    }

    private void addWebhookRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("netty-http").host("localhost").port(port.getPort());

                from("webhook:whatsapp:123?authorizationToken=token&webhookAutoRegister=false" + options).id("webhook")
                        .to("mock:endpoint");
            }
        });
    }
}
