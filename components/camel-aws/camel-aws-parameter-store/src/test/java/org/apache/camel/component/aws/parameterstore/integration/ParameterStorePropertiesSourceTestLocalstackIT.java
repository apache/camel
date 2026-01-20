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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
public class ParameterStorePropertiesSourceTestLocalstackIT extends AwsParameterStoreBaseTest {

    @BeforeAll
    public static void setup() {
        // Simple string parameter
        getSsmClient().putParameter(PutParameterRequest.builder()
                .name("/test/simple")
                .value("hello")
                .type(ParameterType.STRING)
                .build());

        // Database configuration parameters
        getSsmClient().putParameter(PutParameterRequest.builder()
                .name("/myapp/database/host")
                .value("localhost")
                .type(ParameterType.STRING)
                .build());

        getSsmClient().putParameter(PutParameterRequest.builder()
                .name("/myapp/database/port")
                .value("5432")
                .type(ParameterType.STRING)
                .build());

        getSsmClient().putParameter(PutParameterRequest.builder()
                .name("/myapp/database/username")
                .value("admin")
                .type(ParameterType.STRING)
                .build());

        // SecureString parameter (password)
        getSsmClient().putParameter(PutParameterRequest.builder()
                .name("/myapp/database/password")
                .value("secretpassword123")
                .type(ParameterType.SECURE_STRING)
                .build());

        // Parameter with special characters
        getSsmClient().putParameter(PutParameterRequest.builder()
                .name("/myapp/config/connection-string")
                .value("jdbc:postgresql://localhost:5432/mydb?user=admin&password=secret")
                .type(ParameterType.STRING)
                .build());
    }

    @Test
    public void testSimpleParameterFunction() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{aws-parameterstore:/test/simple}}")).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testDatabaseConfigParameters() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:host").setBody(simple("{{aws-parameterstore:/myapp/database/host}}")).to("mock:bar");
                from("direct:port").setBody(simple("{{aws-parameterstore:/myapp/database/port}}")).to("mock:bar");
                from("direct:username").setBody(simple("{{aws-parameterstore:/myapp/database/username}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("localhost", "5432", "admin");

        template.sendBody("direct:host", "Hello World");
        template.sendBody("direct:port", "Hello World");
        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSecureStringParameter() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:password").setBody(simple("{{aws-parameterstore:/myapp/database/password}}")).to("mock:secure");
            }
        });
        context.start();

        getMockEndpoint("mock:secure").expectedBodiesReceived("secretpassword123");

        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testParameterWithDefaultValue() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Non-existent parameter with default value
                from("direct:default").setBody(simple("{{aws-parameterstore:/nonexistent/param:defaultValue}}"))
                        .to("mock:default");
            }
        });
        context.start();

        getMockEndpoint("mock:default").expectedBodiesReceived("defaultValue");

        template.sendBody("direct:default", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testParameterWithSpecialCharacters() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:connstring").setBody(simple("{{aws-parameterstore:/myapp/config/connection-string}}"))
                        .to("mock:special");
            }
        });
        context.start();

        getMockEndpoint("mock:special")
                .expectedBodiesReceived("jdbc:postgresql://localhost:5432/mydb?user=admin&password=secret");

        template.sendBody("direct:connstring", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExistingParameterIgnoresDefault() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Existing parameter should ignore the default value
                from("direct:ignoredefault").setBody(simple("{{aws-parameterstore:/test/simple:shouldBeIgnored}}"))
                        .to("mock:ignoredefault");
            }
        });
        context.start();

        getMockEndpoint("mock:ignoredefault").expectedBodiesReceived("hello");

        template.sendBody("direct:ignoredefault", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
