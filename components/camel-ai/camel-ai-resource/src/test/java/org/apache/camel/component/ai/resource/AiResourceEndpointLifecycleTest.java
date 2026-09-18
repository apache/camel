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
package org.apache.camel.component.ai.resource;

import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AiResourceEndpointLifecycleTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-resource:app_config"
                     + "?resourceUri=camel:///config/app.json"
                     + "&tags=crm"
                     + "&description=Current application configuration"
                     + "&mimeType=application/json")
                        .routeId("app-config-route")
                        .setBody(constant("{\"env\":\"test\"}"));

                from("ai-resource:hidden_config"
                     + "?resourceUri=camel:///config/hidden.json"
                     + "&description=Untagged resource")
                        .routeId("hidden-config-route")
                        .setBody(constant("{}"));
            }
        };
    }

    @Test
    public void testResourceRegisteredOnStart() {
        Set<AiResourceSpec> resources = AiResourceRegistry.getOrCreate(context).getResources().get("crm");

        assertThat(resources)
                .as("Resources registered under 'crm' tag")
                .isNotNull()
                .hasSize(1);

        AiResourceSpec spec = resources.iterator().next();
        assertThat(spec.getName()).isEqualTo("app_config");
        assertThat(spec.getUri()).isEqualTo("camel:///config/app.json");
        assertThat(spec.getDescription()).isEqualTo("Current application configuration");
        assertThat(spec.getMimeType()).isEqualTo("application/json");
        assertThat(spec.isTextual()).isTrue();
        assertThat(spec.getConsumer()).isNotNull();
    }

    @Test
    public void testUntaggedResourceGoesToDefaultPool() {
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(context);

        assertThat(registry.getDefaultResources())
                .as("Untagged resource belongs to the default pool")
                .hasSize(1);
        assertThat(registry.getDefaultResources().iterator().next().getName()).isEqualTo("hidden_config");
        assertThat(registry.getResources())
                .as("Untagged resource must not appear under any tag")
                .containsOnlyKeys("crm");
    }

    @Test
    public void testResourceDeregisteredOnRouteStop() throws Exception {
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(context);
        assertThat(registry.getResources().get("crm")).hasSize(1);

        context.getRouteController().stopRoute("app-config-route");
        assertThat(registry.getResources().get("crm"))
                .as("Resource is deregistered when its route stops")
                .isNull();

        context.getRouteController().startRoute("app-config-route");
        assertThat(registry.getResources().get("crm"))
                .as("Resource is registered again when its route starts")
                .hasSize(1);
    }

    @Test
    public void testResourceDeregisteredOnSuspendAndResume() throws Exception {
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(context);

        context.getRouteController().suspendRoute("app-config-route");
        assertThat(registry.getResources().get("crm"))
                .as("Resource is deregistered while its route is suspended")
                .isNull();

        context.getRouteController().resumeRoute("app-config-route");
        assertThat(registry.getResources().get("crm"))
                .as("Resource is registered again when its route resumes")
                .hasSize(1);
    }

    @Test
    public void testMissingResourceUriFailsRouteStart() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            assertThatThrownBy(() -> {
                ctx.addRoutes(new RouteBuilder() {
                    public void configure() {
                        from("ai-resource:no_uri?tags=crm").setBody(constant("nothing"));
                    }
                });
                ctx.start();
            })
                    .hasStackTraceContaining("The resourceUri option is required");
        }
    }

    @Test
    public void testResourceNameWithSlashIsRejected() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            assertThatThrownBy(() -> ctx.getEndpoint("ai-resource:bad/name?resourceUri=camel:///x"))
                    .hasStackTraceContaining("Resource name must not contain '/'");
        }
    }
}
