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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import io.grpc.ManagedChannelBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.OnCamelContextStopped;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.test.infra.google.secret.manager.services.GoogleSecretManagerLocalContainerInfraService;
import org.apache.camel.test.infra.google.secret.manager.services.GoogleSecretManagerService;
import org.apache.camel.test.infra.google.secret.manager.services.GoogleSecretManagerServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Runs against the Secret Manager service of Floci GCP, or with {@code -Dgoogle.instance.type=remote} against the one
 * of the project of the {@code google.secret.manager.project.id} system property, with the application default
 * credentials, such as the key of the {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable.
 */
class GoogleSecretManagerIT extends CamelTestSupport {

    @RegisterExtension
    static GoogleSecretManagerService service = GoogleSecretManagerServiceFactory.createService();

    final String project = service.projectId();

    @EndpointInject("mock:createSecret")
    private MockEndpoint mockSecret;
    @EndpointInject("mock:getSecret")
    private MockEndpoint mockGetSecret;
    @EndpointInject("mock:deleteSecret")
    private MockEndpoint mockDeleteSecret;
    @EndpointInject("mock:listSecrets")
    private MockEndpoint listSecrets;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        if (service instanceof GoogleSecretManagerLocalContainerInfraService) {
            // the endpoints take the client of the registry, and do not close it: it goes with the context
            SecretManagerServiceClient client = createLocalClient();
            context.getRegistry().bind("secretManagerClient", client);
            context.addLifecycleStrategy(LifecycleStrategySupport.adapt((OnCamelContextStopped) stopped -> client.close()));
        }
        return context;
    }

    // Floci GCP serves gRPC in plain text, and accepts any credentials
    private static SecretManagerServiceClient createLocalClient() throws IOException {
        SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                .setTransportChannelProvider(InstantiatingGrpcChannelProvider.newBuilder()
                        .setEndpoint(service.endpoint())
                        .setChannelConfigurator(ManagedChannelBuilder::usePlaintext)
                        .build())
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();
        return SecretManagerServiceClient.create(settings);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:createSecret")
                        .to("google-secret-manager://" + project + "?operation=createSecret")
                        .to("mock:createSecret");

                from("direct:getSecretVersion").to("google-secret-manager://" + project + "?operation=getSecretVersion")
                        .to("mock:getSecret");

                from("direct:listSecrets").to("google-secret-manager://" + project + "?operation=listSecrets")
                        .to("mock:listSecrets");

                from("direct:deleteSecret").to("google-secret-manager://" + project + "?operation=deleteSecret")
                        .to("mock:deleteSecret");

            }
        };
    }

    @Test
    void sendIn() {

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
