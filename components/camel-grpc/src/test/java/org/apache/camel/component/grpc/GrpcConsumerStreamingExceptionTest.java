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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private ManagedChannel aggregationChannel;
    private ManagedChannel propagationChannel;

    @BeforeEach
    public void startGrpcChannels() {
        aggregationChannel = ManagedChannelBuilder.forAddress("localhost", getRoutePort("grpc-aggregation"))
                .usePlaintext().build();
        propagationChannel = ManagedChannelBuilder.forAddress("localhost", getRoutePort("grpc-propagation"))
                .usePlaintext().build();
    }

    @AfterEach
    public void stopGrpcChannels() {
        if (aggregationChannel != null) {
            aggregationChannel.shutdown().shutdownNow();
        }
        if (propagationChannel != null) {
            propagationChannel.shutdown().shutdownNow();
        }
    }

    @Test
    public void testAggregationFailureIsSentAsError() throws Exception {
        PongResponseStreamObserver responseObserver = call(PingPongGrpc.newStub(aggregationChannel));

        assertNull(responseObserver.pongResponse, "no response must be sent for a failed exchange");
        assertInternalError(responseObserver.error);
    }

    @Test
    public void testPropagationFailureIsSentAsError() throws Exception {
        PongResponseStreamObserver responseObserver = call(PingPongGrpc.newStub(propagationChannel));

        assertNull(responseObserver.pongResponse, "no response must be sent for a failed exchange");
        assertInternalError(responseObserver.error);
    }

    private static PongResponseStreamObserver call(PingPongGrpc.PingPongStub stub) throws InterruptedException {
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();
        StreamObserver<PingRequest> requestObserver = stub.pingAsyncSync(responseObserver);
        requestObserver.onNext(PingRequest.newBuilder().setPingName("PING").setPingId(1).build());
        requestObserver.onCompleted();
        assertTrue(responseObserver.latch.await(5, TimeUnit.SECONDS));
        return responseObserver;
    }

    private static void assertInternalError(Throwable error) {
        StatusRuntimeException e = assertInstanceOf(StatusRuntimeException.class, error);
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals(ROUTE_EXCEPTION_MESSAGE, e.getStatus().getDescription());
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
        private volatile PongResponse pongResponse;
        private volatile Throwable error;

        @Override
        public void onNext(PongResponse value) {
            pongResponse = value;
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }
    }
}
