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
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide your own accessKey and secretKey using -Dcamel.vault.azure.vaultName, -Dcamel.vault.azure.clientId, -Dcamel.vault.azure.clientSecret and -Dcamel.vault.azure.tenantId
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.vault.azure.vaultName", matches = ".*",
                                 disabledReason = "Vault Name not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.azure.clientId", matches = ".*",
                                 disabledReason = "Client Id not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.azure.clientSecret", matches = ".*",
                                 disabledReason = "Client Secret not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.azure.tenantId", matches = ".*",
                                 disabledReason = "Tenant Id not provided"),
})
public class KeyVaultPropertiesSourceNoEnvTestIT extends CamelTestSupport {

    @Test
    public void testFunction() throws Exception {
        context.getVaultConfiguration().azure().setVaultName(System.getProperty("camel.vault.azure.vaultName"));
        context.getVaultConfiguration().azure().setClientId(System.getProperty("camel.vault.azure.clientId"));
        context.getVaultConfiguration().azure().setClientSecret(System.getProperty("camel.vault.azure.clientSecret"));
        context.getVaultConfiguration().azure().setTenantId(System.getProperty("camel.vault.azure.tenantId"));
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

    @Test
    public void testFunctionWithDefault() throws Exception {
        context.getVaultConfiguration().azure().setVaultName(System.getProperty("camel.vault.azure.vaultName"));
        context.getVaultConfiguration().azure().setClientId(System.getProperty("camel.vault.azure.clientId"));
        context.getVaultConfiguration().azure().setClientSecret(System.getProperty("camel.vault.azure.clientSecret"));
        context.getVaultConfiguration().azure().setTenantId(System.getProperty("camel.vault.azure.tenantId"));
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

    @Test
    public void testFunctionWithMultiValues() throws Exception {
        context.getVaultConfiguration().azure().setVaultName(System.getProperty("camel.vault.azure.vaultName"));
        context.getVaultConfiguration().azure().setClientId(System.getProperty("camel.vault.azure.clientId"));
        context.getVaultConfiguration().azure().setClientSecret(System.getProperty("camel.vault.azure.clientSecret"));
        context.getVaultConfiguration().azure().setTenantId(System.getProperty("camel.vault.azure.tenantId"));
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

    @Test
    public void testFunctionWithMultiValuesAndDefault() throws Exception {
        context.getVaultConfiguration().azure().setVaultName(System.getProperty("camel.vault.azure.vaultName"));
        context.getVaultConfiguration().azure().setClientId(System.getProperty("camel.vault.azure.clientId"));
        context.getVaultConfiguration().azure().setClientSecret(System.getProperty("camel.vault.azure.clientSecret"));
        context.getVaultConfiguration().azure().setTenantId(System.getProperty("camel.vault.azure.tenantId"));
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

    @Test
    public void testComplexCustomPropertiesNoDefaultValueFunction() {
        context.getVaultConfiguration().azure().setVaultName(System.getProperty("camel.vault.azure.vaultName"));
        context.getVaultConfiguration().azure().setClientId(System.getProperty("camel.vault.azure.clientId"));
        context.getVaultConfiguration().azure().setClientSecret(System.getProperty("camel.vault.azure.clientSecret"));
        context.getVaultConfiguration().azure().setTenantId(System.getProperty("camel.vault.azure.tenantId"));
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
}
