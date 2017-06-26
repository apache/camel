/**
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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcConsumerAggregationTest extends CamelTestSupport {
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

    @Before
    public void startGrpcChannels() {
        syncRequestChannel = ManagedChannelBuilder.forAddress("localhost", GRPC_SYNC_REQUEST_TEST_PORT).usePlaintext(true).build();
        asyncRequestChannel = ManagedChannelBuilder.forAddress("localhost", GRPC_ASYNC_REQUEST_TEST_PORT).usePlaintext(true).build();
        blockingStub = PingPongGrpc.newBlockingStub(syncRequestChannel);
        nonBlockingStub = PingPongGrpc.newStub(syncRequestChannel);
        asyncNonBlockingStub = PingPongGrpc.newStub(asyncRequestChannel);
    }

    @After
    public void stopGrpcChannels() {
        syncRequestChannel.shutdown().shutdownNow();
        asyncRequestChannel.shutdown().shutdownNow();
    }

    @Test
    public void testSyncSyncMethodInSync() throws Exception {
        LOG.info("gRPC pingSyncSync method blocking test start");
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponse pongResponse = blockingStub.pingSyncSync(pingRequest);

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testSyncAsyncMethodInSync() throws Exception {
        LOG.info("gRPC pingSyncAsync method blocking test start");
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
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
        LOG.info("gRPC pingSyncSync method aync test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        nonBlockingStub.pingSyncSync(pingRequest, responseObserver);
        latch.await(5, TimeUnit.SECONDS);

        PongResponse pongResponse = responseObserver.getPongResponse();
        
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testSyncAsyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingSyncAsync method aync test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        nonBlockingStub.pingSyncAsync(pingRequest, responseObserver);
        latch.await(5, TimeUnit.SECONDS);

        PongResponse pongResponse = responseObserver.getPongResponse();
        
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testAsyncSyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingAsyncSync method aync test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = asyncNonBlockingStub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest);
        requestObserver.onNext(pingRequest);
        requestObserver.onCompleted();
        latch.await(5, TimeUnit.SECONDS);

        PongResponse pongResponse = responseObserver.getPongResponse();
        
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testAsyncAsyncMethodInAsync() throws Exception {
        LOG.info("gRPC pingAsyncAsync method aync test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = asyncNonBlockingStub.pingAsyncAsync(responseObserver);
        requestObserver.onNext(pingRequest);
        requestObserver.onNext(pingRequest);
        requestObserver.onCompleted();
        latch.await(5, TimeUnit.SECONDS);

        PongResponse pongResponse = responseObserver.getPongResponse();
        
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:" + GRPC_SYNC_REQUEST_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION")
                    .bean(new GrpcMessageBuilder(), "buildPongResponse");
                
                from("grpc://localhost:" + GRPC_ASYNC_REQUEST_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION")
                    .bean(new GrpcMessageBuilder(), "buildAsyncPongResponse");
            }
        };
    }
    
    public class PongResponseStreamObserver implements StreamObserver<PongResponse> {
        private PongResponse pongResponse;
        private final CountDownLatch latch;

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

    public class GrpcMessageBuilder {
        public PongResponse buildPongResponse(PingRequest pingRequest) {
            return PongResponse.newBuilder().setPongName(pingRequest.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(pingRequest.getPingId()).build();
        }

        public PongResponse buildAsyncPongResponse(List<PingRequest> pingRequests) {
            return PongResponse.newBuilder().setPongName(pingRequests.get(0).getPingName() + GRPC_TEST_PONG_VALUE).setPongId(pingRequests.get(0).getPingId()).build();
        }
    }
}
