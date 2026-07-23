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
package org.apache.camel.component.cyberark.vault.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.vault.CyberArkVaultConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide CyberArk Conjur connection details using system properties:
// -Dcamel.cyberark.url=http://localhost:8080
// -Dcamel.cyberark.account=myConjurAccount
// -Dcamel.cyberark.username=admin
// -Dcamel.cyberark.apiKey=your-api-key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.cyberark.url", matches = ".*",
                                 disabledReason = "CyberArk Conjur URL not provided"),
        @EnabledIfSystemProperty(named = "camel.cyberark.account", matches = ".*",
                                 disabledReason = "CyberArk Conjur account not provided"),
        @EnabledIfSystemProperty(named = "camel.cyberark.username", matches = ".*",
                                 disabledReason = "CyberArk Conjur username not provided"),
        @EnabledIfSystemProperty(named = "camel.cyberark.apiKey", matches = ".*",
                                 disabledReason = "CyberArk Conjur API key not provided")
})
public class CyberArkVaultPropertiesSourceIT extends CyberArkTestSupport {

    @BeforeAll
    public static void init() throws Exception {

        // Declare variables
        loadPolicy("""
                - !variable database
                - !variable api/credentials
                - !variable simple-secret
                """);

        // Create test secrets in Conjur
        createSecret("database", "{\"username\":\"dbuser\",\"password\":\"dbpass\",\"host\":\"localhost\"}");
        createSecret("api/credentials", "{\"token\":\"secret-token\",\"key\":\"api-key-123\"}");
        createSecret("simple-secret", "my-simple-value");
    }

    @Test
    public void testSimpleSecretRetrieval() throws Exception {
        CyberArkVaultConfiguration cyberark = context.getVaultConfiguration().cyberark();
        cyberark.setUrl(System.getProperty("camel.cyberark.url"));
        cyberark.setAccount(System.getProperty("camel.cyberark.account"));
        cyberark.setUsername(System.getProperty("camel.cyberark.username"));
        cyberark.setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("{{cyberark:simple-secret}}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("my-simple-value");
        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testJsonFieldExtraction() throws Exception {
        CyberArkVaultConfiguration cyberark = context.getVaultConfiguration().cyberark();
        cyberark.setUrl(System.getProperty("camel.cyberark.url"));
        cyberark.setAccount(System.getProperty("camel.cyberark.account"));
        cyberark.setUsername(System.getProperty("camel.cyberark.username"));
        cyberark.setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username")
                        .setBody(simple("{{cyberark:database#username}}"))
                        .to("mock:result");
                from("direct:password")
                        .setBody(simple("{{cyberark:database#password}}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("dbuser", "dbpass");
        template.sendBody("direct:username", "test");
        template.sendBody("direct:password", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testDefaultValue() throws Exception {
        CyberArkVaultConfiguration cyberark = context.getVaultConfiguration().cyberark();
        cyberark.setUrl(System.getProperty("camel.cyberark.url"));
        cyberark.setAccount(System.getProperty("camel.cyberark.account"));
        cyberark.setUsername(System.getProperty("camel.cyberark.username"));
        cyberark.setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("{{cyberark:nonexistent:defaultValue}}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("defaultValue");
        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFieldWithDefaultValue() throws Exception {
        CyberArkVaultConfiguration cyberark = context.getVaultConfiguration().cyberark();
        cyberark.setUrl(System.getProperty("camel.cyberark.url"));
        cyberark.setAccount(System.getProperty("camel.cyberark.account"));
        cyberark.setUsername(System.getProperty("camel.cyberark.username"));
        cyberark.setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("{{cyberark:database#nonexistent:defaultUser}}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("defaultUser");
        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSecretNotFound() {
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
            context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
            context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
            context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .setBody(simple("{{cyberark:nonexistent}}"))
                            .to("mock:result");
                }
            });
            context.start();

            template.sendBody("direct:start", "test");
        });
    }

    @Test
    public void testMultipleSecretsWithFields() throws Exception {
        context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
        context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
        context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
        context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:db")
                        .setBody(simple("{{cyberark:database#host}}"))
                        .to("mock:result");
                from("direct:api")
                        .setBody(simple("{{cyberark:api/credentials#key}}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("localhost", "api-key-123");
        template.sendBody("direct:db", "test");
        template.sendBody("direct:api", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexSecretPath() throws Exception {
        context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
        context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
        context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
        context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("{{cyberark:api/credentials#token}}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("secret-token");
        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }
}
