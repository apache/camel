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
package org.apache.camel.component.aws.secretsmanager.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
public class SecretsManagerPropertiesSourceTestIT extends CamelTestSupport {

    @Test
    public void testFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setBody(simple("{{aws:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testComplexPropertiesFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:username").setBody(simple("{{aws:database_sample:username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:database_sample:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password123");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testComplexCustomPropertiesFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:username").setBody(simple("{{aws:normalkey:username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:normalkey:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo", "pippo");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSecretNotFoundFunction() throws Exception {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").setBody(simple("{{aws:testExample}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            assertMockEndpointsSatisfied();
        });
    }

    @Test
    public void testComplexNoSubkeyPropertiesFunction() throws Exception {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:username").setBody(simple("{{aws:database_sample:not_existent}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            assertMockEndpointsSatisfied();
        });
    }

}
