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
package org.apache.camel.component.aws.parameterstore.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide your own accessKey and secretKey using -Dcamel.vault.aws.accessKey, -Dcamel.vault.aws.secretKey and -Dcamel.vault.aws.region
// Also create the parameters in AWS SSM Parameter Store before running the tests
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.vault.aws.accessKey", matches = ".*",
                                 disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.aws.secretKey", matches = ".*",
                                 disabledReason = "Secret key not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.aws.region", matches = ".*", disabledReason = "Region not provided"),
})
public class ParameterStoreNoEnvPropertiesSourceTestIT extends CamelTestSupport {

    @Test
    public void testSimpleParameterFunction() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{aws-parameterstore:/test/hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testParameterWithHierarchy() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws-parameterstore:/myapp/database/username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws-parameterstore:/myapp/database/password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password123");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testParameterNotFoundFunction() {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").setBody(simple("{{aws-parameterstore:/nonexistent/parameter}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testParameterWithDefaultValue() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default").setBody(simple("{{aws-parameterstore:/nonexistent/param:myDefaultValue}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("myDefaultValue");

        template.sendBody("direct:default", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExistingParameterWithDefaultValue() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Default value should be ignored when parameter exists
                from("direct:existing").setBody(simple("{{aws-parameterstore:/test/hello:shouldBeIgnored}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:existing", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSecureStringParameter() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // SecureString parameters should be automatically decrypted
                from("direct:secure").setBody(simple("{{aws-parameterstore:/myapp/secrets/api-key}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("my-secret-api-key");

        template.sendBody("direct:secure", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMultipleParametersInRoute() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(System.getProperty("camel.vault.aws.accessKey"));
        context.getVaultConfiguration().aws().setSecretKey(System.getProperty("camel.vault.aws.secretKey"));
        context.getVaultConfiguration().aws().setRegion(System.getProperty("camel.vault.aws.region"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:multi")
                        .setBody(simple(
                                "Host: {{aws-parameterstore:/myapp/database/host}}, Port: {{aws-parameterstore:/myapp/database/port}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("Host: localhost, Port: 5432");

        template.sendBody("direct:multi", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
