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
package org.apache.camel.component.google.secret.manager.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide your own accessKey and secretKey using -Dcamel.vault.gcp.serviceAccountKey and -Dcamel.vault.gcp.projectId
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.vault.gcp.serviceAccountKey", matches = ".*",
                                 disabledReason = "Service Account Key not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.gcp.projectId", matches = ".*",
                                 disabledReason = "Project Id not provided")
})
public class GoogleSecretManagerPropertiesNoEnvSourceTestIT extends CamelTestSupport {

    @Test
    public void testFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{gcp:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexPropertiesFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{gcp:database_sample/username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{gcp:database_sample/password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password123");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexPropertiesWithDefaultFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{gcp:database_sample/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{gcp:database_sample/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password123");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSecretNotFoundFunction() {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").setBody(simple("{{gcp:testExample}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexCustomPropertiesDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{gcp:postgresql/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{gcp:postgresql/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexCustomPropertiesDefaultValueExceptionFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{gcp:test-3/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{gcp:test-3/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexCustomPropertiesExceptionFunction() {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{gcp:test-3/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{gcp:test-3/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexSimpleDefaultValueExceptionFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{gcp:test-3:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{gcp:test-1:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{gcp:hello@1}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("\"id\":\"23\"");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionAndNoFieldFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{gcp:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{\"id\":\"23\"}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionNoFieldAndDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{gcp:hello:pippo@2}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentSecretFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{gcp:test1:pippo@e8d0e680-a504-4b70-a9b2-acf5efe0ba23}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentVersionFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{gcp:test1:pippo@e8d0e680-a504-4b70-a9b2-acf5efe0ba29}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().gcp().setServiceAccountKey(System.getProperty("camel.vault.gcp.serviceAccountKey"));
        context.getVaultConfiguration().gcp().setProjectId(System.getProperty("camel.vault.gcp.projectId"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{gcp:hello/id@3}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("23");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
