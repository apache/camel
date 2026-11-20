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
package org.apache.camel.component.grpc;

import java.net.ServerSocket;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A producer with streamRepliesTo (SIMPLE strategy): the exchange that made a call fails when the call fails, unless
 * the error is routed to the replies endpoint (forwardOnError).
 */
class GrpcProducerStreamRepliesToTest extends CamelTestSupport {

    private static final String SERVICE = "/org.apache.camel.component.grpc.PingPong";

    private Server grpcServer;
    private int unusedPort;

    @Override
    protected void setupResources() throws Exception {
        grpcServer = ServerBuilder.forPort(0).addService(new PingPongImpl()).build().start();
        // a port that nothing listens on
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
    }

    @Override
    protected void cleanupResources() {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    void testFailedCallFailsTheExchange() throws Exception {
        Exchange exchange = template.send("direct:simple",
                e -> e.getIn().setBody(PingRequest.newBuilder().setPingName("error").build()));

        StatusRuntimeException cause = assertInstanceOf(StatusRuntimeException.class, exchange.getException());
        assertEquals(Status.Code.UNAVAILABLE, cause.getStatus().getCode());
    }

    @Test
    void testServerNotReachableFailsTheExchange() throws Exception {
        Exchange exchange = template.send("direct:unreachable",
                e -> e.getIn().setBody(PingRequest.newBuilder().setPingName("ok").build()));

        StatusRuntimeException cause = assertInstanceOf(StatusRuntimeException.class, exchange.getException());
        assertEquals(Status.Code.UNAVAILABLE, cause.getStatus().getCode());
    }

    @Test
    void testSuccessfulCallRoutesTheReplies() throws Exception {
        MockEndpoint replies = getMockEndpoint("mock:simple-replies");
        replies.expectedBodiesReceived(
                PongResponse.newBuilder().setPongName("Hello ok 1").build(),
                PongResponse.newBuilder().setPongName("Hello ok 2").build());

        Exchange exchange = template.send("direct:simple",
                e -> e.getIn().setBody(PingRequest.newBuilder().setPingName("ok").build()));

        assertNull(exchange.getException());
        replies.assertIsSatisfied();
    }

    @Test
    void testForwardedErrorIsRoutedToTheReplies() throws Exception {
        MockEndpoint replies = getMockEndpoint("mock:forward-replies");
        replies.expectedMessageCount(1);
        replies.expectedHeaderReceived(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR);

        Exchange exchange = template.send("direct:simple-forward-on-error",
                e -> e.getIn().setBody(PingRequest.newBuilder().setPingName("error").build()));

        // the error is handled by the route of the replies, as forwardOnError asks
        assertNull(exchange.getException());
        replies.assertIsSatisfied();
        assertInstanceOf(StatusRuntimeException.class, replies.getExchanges().get(0).getIn().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String grpc = "grpc://localhost:" + grpcServer.getPort() + SERVICE;

                from("direct:simple")
                        .to(grpc + "?method=pingSyncAsync&streamRepliesTo=direct:simple-replies");
                from("direct:simple-replies").to("mock:simple-replies");

                from("direct:simple-forward-on-error")
                        .to(grpc + "?method=pingSyncAsync&streamRepliesTo=direct:forward-replies&forwardOnError=true");
                from("direct:forward-replies").to("mock:forward-replies");

                from("direct:unreachable")
                        .to("grpc://localhost:" + unusedPort + SERVICE
                            + "?method=pingSyncAsync&streamRepliesTo=direct:simple-replies");
            }
        };
    }

    static class PingPongImpl extends PingPongGrpc.PingPongImplBase {

        @Override
        public void pingSyncAsync(PingRequest request, StreamObserver<PongResponse> responseObserver) {
            if ("error".equals(request.getPingName())) {
                responseObserver.onError(Status.UNAVAILABLE.withDescription("Requested error").asRuntimeException());
                return;
            }
            for (int i = 1; i <= 2; i++) {
                responseObserver.onNext(
                        PongResponse.newBuilder().setPongName("Hello " + request.getPingName() + " " + i).build());
            }
            responseObserver.onCompleted();
        }
    }
}
