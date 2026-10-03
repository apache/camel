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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the AGGREGATION and PROPAGATION consumer strategies a failed exchange must end the call with an error, as for
 * unary calls, and not send the message body as a response.
 */
public class GrpcConsumerStreamingExceptionTest extends GrpcTestSupport {

    private static final String ROUTE_EXCEPTION_MESSAGE = "GRPC Camel streaming exception message";
    private static final String MUTED_EXCEPTION_MESSAGE = "Exchange processing failed";

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    @AfterEach
    public void stopGrpcChannels() {
        channels.values().forEach(channel -> channel.shutdown().shutdownNow());
        channels.clear();
    }

    @Test
    public void testAggregationFailureIsSentAsError() throws Exception {
        PongResponseStreamObserver responseObserver = callClientStreaming("grpc-aggregation");

        assertTrue(responseObserver.pongResponses.isEmpty(), "no response must be sent for a failed exchange");
        assertInternalError(responseObserver, ROUTE_EXCEPTION_MESSAGE);
    }

    @Test
    public void testPropagationFailureIsSentAsError() throws Exception {
        PongResponseStreamObserver responseObserver = callClientStreaming("grpc-propagation");

        assertTrue(responseObserver.pongResponses.isEmpty(), "no response must be sent for a failed exchange");
        assertInternalError(responseObserver, ROUTE_EXCEPTION_MESSAGE);
    }

    @Test
    public void testPropagationFailureEndsTheCall() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:propagation");
        mock.expectedMessageCount(1);
        // a message routed after the failure would arrive later than the error
        mock.setAssertPeriod(500);

        PongResponseStreamObserver responseObserver = callBidiStreaming("grpc-propagation", 2);

        assertTrue(responseObserver.pongResponses.isEmpty(), "no response must be sent for a failed exchange");
        assertInternalError(responseObserver, ROUTE_EXCEPTION_MESSAGE);
        mock.assertIsSatisfied();
    }

    @Test
    public void testAggregationFailureIsMutedByDefault() throws Exception {
        PongResponseStreamObserver responseObserver = callClientStreaming("grpc-aggregation-muted");

        assertTrue(responseObserver.pongResponses.isEmpty(), "no response must be sent for a failed exchange");
        assertInternalError(responseObserver, MUTED_EXCEPTION_MESSAGE);
    }

    @Test
    public void testPropagationFailureIsMutedByDefault() throws Exception {
        PongResponseStreamObserver responseObserver = callClientStreaming("grpc-propagation-muted");

        assertTrue(responseObserver.pongResponses.isEmpty(), "no response must be sent for a failed exchange");
        assertInternalError(responseObserver, MUTED_EXCEPTION_MESSAGE);
    }

    @Test
    public void testPropagationHandledFailureKeepsTheStreamOpen() throws Exception {
        PongResponseStreamObserver responseObserver = callBidiStreaming("grpc-propagation-handled", 2);

        assertNull(responseObserver.error, "a handled exception must not end the call with an error");
        assertEquals(1, responseObserver.completed.get());
        List<Integer> pongIds = new ArrayList<>();
        responseObserver.pongResponses.forEach(pong -> pongIds.add(pong.getPongId()));
        assertEquals(List.of(1, 2), pongIds);
    }

    // client streaming call (one response) with a single message
    private PongResponseStreamObserver callClientStreaming(String routeId) throws InterruptedException {
        return call(routeId, 1, false);
    }

    // bidirectional streaming call, so that each message can be answered
    private PongResponseStreamObserver callBidiStreaming(String routeId, int messages) throws InterruptedException {
        return call(routeId, messages, true);
    }

    private PongResponseStreamObserver call(String routeId, int messages, boolean bidi) throws InterruptedException {
        ManagedChannel channel = channels.computeIfAbsent(routeId,
                id -> ManagedChannelBuilder.forAddress("localhost", getRoutePort(id)).usePlaintext().build());
        PingPongGrpc.PingPongStub stub = PingPongGrpc.newStub(channel);
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();
        StreamObserver<PingRequest> requestObserver
                = bidi ? stub.pingAsyncAsync(responseObserver) : stub.pingAsyncSync(responseObserver);
        for (int i = 1; i <= messages; i++) {
            requestObserver.onNext(PingRequest.newBuilder().setPingName("PING").setPingId(i).build());
        }
        requestObserver.onCompleted();
        assertTrue(responseObserver.latch.await(5, TimeUnit.SECONDS));
        return responseObserver;
    }

    private static void assertInternalError(PongResponseStreamObserver responseObserver, String description) {
        StatusRuntimeException e = assertInstanceOf(StatusRuntimeException.class, responseObserver.error);
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals(description, e.getStatus().getDescription());
        assertEquals(0, responseObserver.completed.get(), "a failed call must not also complete");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=AGGREGATION&muteException=false")
                        .routeId("grpc-aggregation")
                        .bean(new GrpcMessageBuilder(), "buildAggregatedPongResponse")
                        .throwException(CamelException.class, ROUTE_EXCEPTION_MESSAGE);

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=PROPAGATION&muteException=false")
                        .routeId("grpc-propagation")
                        .bean(new GrpcMessageBuilder(), "buildPongResponse")
                        .to("mock:propagation")
                        .throwException(CamelException.class, ROUTE_EXCEPTION_MESSAGE);

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=AGGREGATION")
                        .routeId("grpc-aggregation-muted")
                        .bean(new GrpcMessageBuilder(), "buildAggregatedPongResponse")
                        .throwException(CamelException.class, ROUTE_EXCEPTION_MESSAGE);

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=PROPAGATION")
                        .routeId("grpc-propagation-muted")
                        .bean(new GrpcMessageBuilder(), "buildPongResponse")
                        .throwException(CamelException.class, ROUTE_EXCEPTION_MESSAGE);

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=PROPAGATION&muteException=true")
                        .routeId("grpc-propagation-handled")
                        .onException(CamelException.class).handled(true).end()
                        .bean(new GrpcMessageBuilder(), "buildPongResponse")
                        .throwException(CamelException.class, ROUTE_EXCEPTION_MESSAGE);
            }
        };
    }

    public static class GrpcMessageBuilder {
        public PongResponse buildAggregatedPongResponse(List<PingRequest> pingRequests) {
            return buildPongResponse(pingRequests.get(0));
        }

        public PongResponse buildPongResponse(PingRequest pingRequest) {
            return PongResponse.newBuilder().setPongName(pingRequest.getPingName() + "PONG")
                    .setPongId(pingRequest.getPingId()).build();
        }
    }

    static class PongResponseStreamObserver implements StreamObserver<PongResponse> {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<PongResponse> pongResponses = new CopyOnWriteArrayList<>();
        private final AtomicInteger completed = new AtomicInteger();
        private volatile Throwable error;

        @Override
        public void onNext(PongResponse value) {
            pongResponses.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            completed.incrementAndGet();
            latch.countDown();
        }
    }
}
