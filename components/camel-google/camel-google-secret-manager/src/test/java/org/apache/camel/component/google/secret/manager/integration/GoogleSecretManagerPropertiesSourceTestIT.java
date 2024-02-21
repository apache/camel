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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class GoogleSecretManagerPropertiesSourceTestIT extends CamelTestSupport {

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexPropertiesFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexPropertiesWithDefaultFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testSecretNotFoundFunction() {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultValueFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultValueExceptionFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexCustomPropertiesExceptionFunction() {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexSimpleDefaultValueExceptionFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testComplexPropertiesDefaultInstanceFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionAndNoFieldFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldAndDefaultValueFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentSecretFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentVersionFunction() throws Exception {
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

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_GCP_PROJECT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunction() throws Exception {
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
