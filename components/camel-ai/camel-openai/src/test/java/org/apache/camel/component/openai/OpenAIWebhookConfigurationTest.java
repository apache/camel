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
package org.apache.camel.component.openai;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.spi.ComponentResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the webhook operation refuses to start with, and which side of a route each operation belongs on.
 */
class OpenAIWebhookConfigurationTest {

    private static final String SECRET = "whsec_c2VjcmV0";

    @Test
    void theWebhookOperationNeedsASecret() {
        assertThatThrownBy(() -> start("openai:webhook"))
                .hasStackTraceContaining("webhookSecret is required");
    }

    @Test
    void theWebhookOperationNeedsAnHttpServer() {
        assertThatThrownBy(() -> {
            // camel-platform-http-vertx is on the test classpath, so it takes a context that cannot resolve it
            try (CamelContext context = new DefaultCamelContext()) {
                ComponentResolver resolver = new DefaultComponentResolver();
                context.getCamelContextExtension().addContextPlugin(ComponentResolver.class,
                        (name, ctx) -> "platform-http".equals(name) ? null : resolver.resolveComponent(name, ctx));
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("openai:webhook?webhookSecret=" + SECRET).to("mock:events");
                    }
                });
                context.start();
            }
        }).hasStackTraceContaining("No RestConsumerFactory found");
    }

    @Test
    void theWebhookOperationStartsWithASecretAndAnHttpServer() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            RecordingRestConsumerFactory factory = RecordingRestConsumerFactory.bindTo(context);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("openai:webhook?webhookSecret=" + SECRET
                         + "&httpServerComponent=recordingRestConsumerFactory").to("mock:events");
                }
            });
            context.start();

            assertThat(factory.registrations()).singleElement()
                    .satisfies(registration -> assertThat(registration.path()).isEqualTo("/openai/webhook"));
        }
    }

    @Test
    void theWebhookOperationIsNotSentTo() {
        assertThatThrownBy(() -> {
            try (CamelContext context = new DefaultCamelContext()) {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").to("openai:webhook?webhookSecret=" + SECRET);
                    }
                });
                context.start();
            }
        }).hasStackTraceContaining("receives events");
    }

    @Test
    void anotherOperationIsNotConsumedFrom() {
        assertThatThrownBy(() -> start("openai:moderation?apiKey=test-key"))
                .hasStackTraceContaining("Only the webhook operation can be consumed from");
    }

    private void start(String uri) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(uri).to("mock:events");
                }
            });
            context.start();
        }
    }
}
