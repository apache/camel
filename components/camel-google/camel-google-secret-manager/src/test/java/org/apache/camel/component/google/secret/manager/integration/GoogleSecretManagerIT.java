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

import java.util.concurrent.atomic.AtomicInteger;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".*",
                              disabledReason = "Application credentials were not provided")
public class GoogleSecretManagerIT extends CamelTestSupport {

    final String serviceAccountKeyFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    final String project = "myProject";

    @EndpointInject("mock:createSecret")
    private MockEndpoint mockSecret;
    @EndpointInject("mock:getSecret")
    private MockEndpoint mockGetSecret;
    @EndpointInject("mock:deleteSecret")
    private MockEndpoint mockDeleteSecret;
    @EndpointInject("mock:listSecrets")
    private MockEndpoint listSecrets;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:createSecret")
                        .to("google-secret-manager://" + project + "?serviceAccountKey="
                            + serviceAccountKeyFile + "&operation=createSecret")
                        .to("mock:createSecret");

                from("direct:getSecretVersion").to("google-secret-manager://" + project + "?serviceAccountKey="
                                                   + serviceAccountKeyFile + "&operation=getSecretVersion")
                        .to("mock:getSecret");

                from("direct:listSecrets").to("google-secret-manager://" + project + "?serviceAccountKey="
                                              + serviceAccountKeyFile + "&operation=listSecrets")
                        .to("mock:listSecrets");

                from("direct:deleteSecret").to("google-secret-manager://" + project + "?serviceAccountKey="
                                               + serviceAccountKeyFile + "&operation=deleteSecret")
                        .to("mock:deleteSecret");

            }
        };
    }

    @Test
    public void sendIn() {

        mockSecret.expectedMessageCount(1);
        mockGetSecret.expectedMessageCount(1);
        mockDeleteSecret.expectedMessageCount(1);

        template.send("direct:createSecret", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test123");
                exchange.getMessage().setBody("Hello");
            }
        });
        Exchange ex = template.request("direct:getSecretVersion", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test123");
                exchange.getMessage().setHeader(GoogleSecretManagerConstants.VERSION_ID, "1");
            }
        });

        assertEquals("Hello", ex.getMessage().getBody());

        ex = template.request("direct:getSecretVersion", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test123");
            }
        });

        assertEquals("Hello", ex.getMessage().getBody());

        ex = template.request("direct:listSecrets", new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        SecretManagerServiceClient.ListSecretsPagedResponse response
                = ex.getMessage().getBody(SecretManagerServiceClient.ListSecretsPagedResponse.class);
        AtomicInteger totalSecret = new AtomicInteger();
        response
                .iterateAll()
                .forEach(
                        secret -> {
                            totalSecret.getAndIncrement();
                        });

        assertEquals(1, totalSecret.get());
        ex = template.request("direct:deleteSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test123");
            }
        });

        assertNotNull(ex.getMessage());
    }

}
