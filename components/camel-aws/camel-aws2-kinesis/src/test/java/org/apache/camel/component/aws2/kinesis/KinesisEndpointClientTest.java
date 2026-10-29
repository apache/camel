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
package org.apache.camel.component.aws2.kinesis;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Each aws2-kinesis endpoint uses the client of its own configuration, and stopping one endpoint does not close the
 * client of another.
 */
public class KinesisEndpointClientTest {

    // nothing listens there: a call with an open client fails to connect, a call with a closed client fails at once
    private static final String NO_SERVER = "&overrideEndpoint=true&uriEndpointOverride=http://localhost:1";
    private static final String STREAM_A = "aws2-kinesis:streamA?accessKey=a&secretKey=a&region=us-east-1" + NO_SERVER;
    private static final String STREAM_B = "aws2-kinesis:streamB?accessKey=b&secretKey=b&region=eu-west-1" + NO_SERVER;

    private CamelContext context;

    @BeforeEach
    public void createContext() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    public void stopContext() {
        context.stop();
    }

    @Test
    public void eachEndpointCreatesTheClientOfItsOwnConfiguration() throws Exception {
        addRoutes(STREAM_A, STREAM_B);
        context.start();

        KinesisClient a = context.getEndpoint(STREAM_A, Kinesis2Endpoint.class).getClient();
        KinesisClient b = context.getEndpoint(STREAM_B, Kinesis2Endpoint.class).getClient();
        assertNotSame(a, b);
        assertEquals("us-east-1", a.serviceClientConfiguration().region().id());
        assertEquals("eu-west-1", b.serviceClientConfiguration().region().id());
    }

    @Test
    public void eachEndpointCreatesTheAsyncClientOfItsOwnConfiguration() throws Exception {
        String a = STREAM_A + "&asyncClient=true";
        String b = STREAM_B + "&asyncClient=true";
        addRoutes(a, b);
        context.start();

        assertEquals("us-east-1",
                context.getEndpoint(a, Kinesis2Endpoint.class).getAsyncClient().serviceClientConfiguration().region()
                        .id());
        assertEquals("eu-west-1",
                context.getEndpoint(b, Kinesis2Endpoint.class).getAsyncClient().serviceClientConfiguration().region()
                        .id());
    }

    @Test
    public void producerUsesTheClientConfiguredOnItsEndpoint() throws Exception {
        KinesisClient clientA = mockClient();
        KinesisClient clientB = mockClient();
        context.getRegistry().bind("clientA", clientA);
        context.getRegistry().bind("clientB", clientB);
        addRoutes("aws2-kinesis:streamA?amazonKinesisClient=#clientA", "aws2-kinesis:streamB?amazonKinesisClient=#clientB");
        context.start();

        send("direct:b");

        Mockito.verify(clientB).putRecord(Mockito.any(PutRecordRequest.class));
        Mockito.verify(clientA, Mockito.never()).putRecord(Mockito.any(PutRecordRequest.class));
    }

    @Test
    public void removingARouteKeepsTheClientOfAnotherEndpointOpen() throws Exception {
        addRoutes(STREAM_A, STREAM_A.replace("streamA", "streamB"));
        context.start();

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");

        // still connects (and fails to connect), instead of failing with "Connection pool shut down"
        Exception cause = send("direct:b").getException();
        assertInstanceOf(SdkClientException.class, cause, () -> "unexpected failure: " + cause);
    }

    @Test
    public void clientConfiguredOnTheComponentIsNotClosed() throws Exception {
        KinesisClient client = mockClient();
        context.getComponent("aws2-kinesis", Kinesis2Component.class).getConfiguration().setAmazonKinesisClient(client);
        addRoutes("aws2-kinesis:streamA", "aws2-kinesis:streamB");
        context.start();
        send("direct:a");

        context.stop();

        Mockito.verify(client).putRecord(Mockito.any(PutRecordRequest.class));
        Mockito.verify(client, Mockito.never()).close();
    }

    private void addRoutes(String a, String b) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to(a);
                from("direct:b").routeId("b").to(b);
            }
        });
    }

    private Exchange send(String uri) {
        return context.createProducerTemplate().send(uri, exchange -> {
            exchange.getMessage().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
            exchange.getMessage().setBody("record");
        });
    }

    private static KinesisClient mockClient() {
        KinesisClient client = Mockito.mock(KinesisClient.class);
        Mockito.when(client.putRecord(Mockito.any(PutRecordRequest.class)))
                .thenReturn(PutRecordResponse.builder().sequenceNumber("1").shardId("shardId-000000000000").build());
        return client;
    }
}
