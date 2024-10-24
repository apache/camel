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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;

public class SecretsManagerPropertiesSourceTestLocalstackIT extends AwsSecretsManagerBaseTest {

    private static String secretVersion;

    @BeforeAll
    public static void setup() {
        // Base secret
        CreateSecretRequest.Builder builder = CreateSecretRequest.builder();
        builder.name("test");
        builder.secretString("hello");
        getSecretManagerClient().createSecret(builder.build());

        // Json multifield Secret
        builder = CreateSecretRequest.builder();
        builder.name("testJson");
        builder.secretString("{\n" +
                             "  \"username\": \"admin\",\n" +
                             "  \"password\": \"password\",\n" +
                             "  \"host\": \"myhost.com\"\n" +
                             "}");
        getSecretManagerClient().createSecret(builder.build());

        // Json multifield Secret
        builder = CreateSecretRequest.builder();
        builder.name("testJsonVersioned");
        builder.secretString("{\n" +
                             "  \"username\": \"admin\",\n" +
                             "  \"password\": \"password\",\n" +
                             "  \"host\": \"myhost.com\"\n" +
                             "}");
        getSecretManagerClient().createSecret(builder.build());

        // Json versioned multifield Secret
        PutSecretValueRequest.Builder builderPutSecValue = PutSecretValueRequest.builder();
        builderPutSecValue.secretId("testJsonVersioned");
        builderPutSecValue.secretString("{\n" +
                                        "  \"username\": \"admin\",\n" +
                                        "  \"password\": \"admin123\",\n" +
                                        "  \"host\": \"myhost.com\"\n" +
                                        "}");
        PutSecretValueResponse resp = getSecretManagerClient().putSecretValue(builderPutSecValue.build());
        secretVersion = resp.versionId();
    }

    @Test
    public void testFunction() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{aws:test}}")).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionJson() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:testJson#username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:testJson#password}}")).to("mock:bar");
                from("direct:host").setBody(simple("{{aws:testJson#host}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password", "myhost.com");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        template.sendBody("direct:host", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionJsonWithVersion() throws Exception {
        context.getVaultConfiguration().aws().setAccessKey(getAccessKey());
        context.getVaultConfiguration().aws().setSecretKey(getSecretKey());
        context.getVaultConfiguration().aws().setRegion(getRegion());
        context.getVaultConfiguration().aws().setOverrideEndpoint(true);
        context.getVaultConfiguration().aws().setUriEndpointOverride(getUrlOverride());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:usernameVersioned").setBody(simple("{{aws:testJsonVersioned#username@" + secretVersion + "}}"))
                        .to("mock:put");
                from("direct:passwordVersioned").setBody(simple("{{aws:testJsonVersioned#password@" + secretVersion + "}}"))
                        .to("mock:put");
                from("direct:hostVersioned").setBody(simple("{{aws:testJsonVersioned#host@" + secretVersion + "}}"))
                        .to("mock:put");
            }
        });
        context.start();

        getMockEndpoint("mock:put").expectedBodiesReceived("admin", "admin123", "myhost.com");

        template.sendBody("direct:usernameVersioned", "Hello World");
        template.sendBody("direct:passwordVersioned", "Hello World");
        template.sendBody("direct:hostVersioned", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
