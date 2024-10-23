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

public class SecretsManagerPropertiesSourceTestLocalstackIT extends AwsSecretsManagerBaseTest {

    @BeforeAll
    public static void setup() {
        CreateSecretRequest.Builder builder = CreateSecretRequest.builder();
        builder.name("test");
        builder.secretString("hello");
        getSecretManagerClient().createSecret(builder.build());
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
                from("direct:start").setBody(simple("{{aws:test}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }
}
