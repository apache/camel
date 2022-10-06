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
package org.apache.camel.component.azure.key.vault.integration.operations;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class KeyVaultPropertiesSourceTestIT extends CamelTestSupport {

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{azure:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("test");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testFunctionWithDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{azure:hello:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("test");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testFunctionWithMultiValues() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{azure:database/username}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testFunctionWithMultiValuesAndDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{azure:dbsample/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{azure:dbsample/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("oscerd", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testComplexCustomPropertiesNoDefaultValueFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{azure:postgresql/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{azure:postgresql/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{azure:database/password@79be21dd88774b91aff2dfa40fa9ea77}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("password123");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionAndNoFieldFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{azure:database@79be21dd88774b91aff2dfa40fa9ea77}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived(
                "{\"username\":\"admin\",\"password\":\"password123\",\"engine\":\"postgres\",\"host\":\"127.0.0.1\",\"port\":\"3128\",\"dbname\":\"db\"}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldAndDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{azure:database:pippo@79be21dd88774b91aff2dfa40fa9ea77}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived(
                "{\"username\":\"admin\",\"password\":\"password123\",\"engine\":\"postgres\",\"host\":\"127.0.0.1\",\"port\":\"3128\",\"dbname\":\"db\"}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentSecretFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{azure:database:pippo@79be21dd88774b91aff2dfa40fa9ea78}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentVersionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{azure:database1:pippo@79be21dd88774b91aff2dfa40fa9ea77}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_VAULT_NAME", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_CLIENT_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AZURE_TENANT_ID", matches = ".*")
    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{azure:database/password:pippo@79be21dd88774b91aff2dfa40fa9ea77}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("password123");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
