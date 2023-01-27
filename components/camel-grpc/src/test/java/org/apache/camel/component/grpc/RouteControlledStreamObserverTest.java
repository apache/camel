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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class RouteControlledStreamObserverTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumerAggregationTest.class);

    private static final int GRPC_SYNC_REQUEST_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_ASYNC_REQUEST_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private ManagedChannel syncRequestChannel;
    private ManagedChannel asyncRequestChannel;
    private PingPongGrpc.PingPongBlockingStub blockingStub;
    private PingPongGrpc.PingPongStub nonBlockingStub;
    private PingPongGrpc.PingPongStub asyncNonBlockingStub;

    @BeforeEach
    public void startGrpcChannels() {
        syncRequestChannel = ManagedChannelBuilder.forAddress("localhost", GRPC_SYNC_REQUEST_TEST_PORT).usePlaintext().build();
        asyncRequestChannel
                = ManagedChannelBuilder.forAddress("localhost", GRPC_ASYNC_REQUEST_TEST_PORT).usePlaintext().build();
        blockingStub = PingPongGrpc.newBlockingStub(syncRequestChannel);
        nonBlockingStub = PingPongGrpc.newStub(syncRequestChannel);
        asyncNonBlockingStub = PingPongGrpc.newStub(asyncRequestChannel);
    }

    @AfterEach
    public void stopGrpcChannels() {
        if (syncRequestChannel != null) {
            syncRequestChannel.shutdown().shutdownNow();
        }
        if (asyncRequestChannel != null) {
            asyncRequestChannel.shutdown().shutdownNow();
        }
    }

    @Test
    public void testSyncSyncMethodInSync() {
        LOG.info("gRPC pingSyncSync method blocking test start");
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponse pongResponse = blockingStub.pingSyncSync(pingRequest);

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testSyncAsyncMethodInSync() {
        LOG.info("gRPC pingSyncAsync method blocking test start");
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        Iterator<PongResponse> pongResponseIter = blockingStub.pingSyncAsync(pingRequest);
        while (pongResponseIter.hasNext()) {
            PongResponse pongResponse = pongResponseIter.next();
            assertNotNull(pongResponse);
            assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
            assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
        }
    }

    @Test
    public void testSyncSyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingSyncSync method async test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        nonBlockingStub.pingSyncSync(pingRequest, responseObserver);
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        PongResponse pongResponse = responseObserver.getPongResponse();

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testSyncAsyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingSyncAsync method async test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        nonBlockingStub.pingSyncAsync(pingRequest, responseObserver);
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        PongResponse pongResponse = responseObserver.getPongResponse();

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testAsyncSyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingAsyncSync method async test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = asyncNonBlockingStub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest);
        requestObserver.onNext(pingRequest);
        requestObserver.onCompleted();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        PongResponse pongResponse = responseObserver.getPongResponse();

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testAsyncAsyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingAsyncAsync method async test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = asyncNonBlockingStub.pingAsyncAsync(responseObserver);
        requestObserver.onNext(pingRequest);
        requestObserver.onNext(pingRequest);
        requestObserver.onCompleted();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        PongResponse pongResponse = responseObserver.getPongResponse();

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void unsupportedEndpointConfigurationFailureTest() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:" + GRPC_SYNC_REQUEST_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION" +
                     "&routeControlledStreamObserver=true").to("log:foo");
            }
        });
        assertThrows(IllegalArgumentException.class, camelContext::start);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            private void process(Exchange exchange) {
                Message message = exchange.getIn();
                PingRequest pingRequest = message.getBody(PingRequest.class);
                StreamObserver<Object> responseObserver
                        = (StreamObserver<Object>) exchange.getProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER);
                PongResponse pongResponse
                        = PongResponse.newBuilder().setPongName(pingRequest.getPingName() + GRPC_TEST_PONG_VALUE)
                                .setPongId(pingRequest.getPingId()).build();
                message.setBody(pongResponse, PongResponse.class);
                exchange.setMessage(message);
                responseObserver.onNext(pongResponse);
                responseObserver.onCompleted();
            }

            @Override
            public void configure() {
                from("grpc://localhost:" + GRPC_SYNC_REQUEST_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=PROPAGATION&routeControlledStreamObserver=true")
                        .process(this::process);

                from("grpc://localhost:" + GRPC_ASYNC_REQUEST_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION")
                        .bean(new GrpcMessageBuilder(), "buildAsyncPongResponse");
            }
        };
    }

    public static class PongResponseStreamObserver implements StreamObserver<PongResponse> {
        private final CountDownLatch latch;
        private PongResponse pongResponse;

        public PongResponseStreamObserver(CountDownLatch latch) {
            this.latch = latch;
        }

        public PongResponse getPongResponse() {
            return pongResponse;
        }

        @Override
        public void onNext(PongResponse value) {
            pongResponse = value;
        }

        @Override
        public void onError(Throwable t) {
            LOG.info("Exception", t);
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }
    }

    public static class GrpcMessageBuilder {
        @SuppressWarnings("unused")
        public PongResponse buildAsyncPongResponse(List<PingRequest> pingRequests) {
            return PongResponse.newBuilder().setPongName(pingRequests.get(0).getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(pingRequests.get(0).getPingId()).build();
        }
    }
}
