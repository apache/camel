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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcConsumerPropagationTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumerPropagationTest.class);

    private static final int GRPC_ASYNC_NEXT_REQUEST_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_ASYNC_COMPLETED_REQUEST_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private ManagedChannel asyncOnNextChannel;
    private ManagedChannel asyncOnCompletedChannel;
    private PingPongGrpc.PingPongStub asyncOnNextStub;
    private PingPongGrpc.PingPongStub asyncOnCompletedStub;

    @Before
    public void startGrpcChannels() {
        asyncOnNextChannel = ManagedChannelBuilder.forAddress("localhost", GRPC_ASYNC_NEXT_REQUEST_TEST_PORT).usePlaintext().build();
        asyncOnCompletedChannel = ManagedChannelBuilder.forAddress("localhost", GRPC_ASYNC_COMPLETED_REQUEST_TEST_PORT).usePlaintext().build();
        asyncOnNextStub = PingPongGrpc.newStub(asyncOnNextChannel);
        asyncOnCompletedStub = PingPongGrpc.newStub(asyncOnCompletedChannel);
    }

    @After
    public void stopGrpcChannels() throws Exception {
        asyncOnNextChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        asyncOnCompletedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testOnNextPropagation() throws Exception {
        LOG.info("gRPC pingAsyncSync method aync test start");
        
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = asyncOnNextStub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest);
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:async-on-next-propagation");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_METHOD_NAME_HEADER, "pingAsyncSync");
        mockEndpoint.assertIsSatisfied();
        
        PongResponse pongResponse = responseObserver.getPongResponse();
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }


    @Test
    public void testOnCompletedPropagation() throws Exception {
        LOG.info("gRPC pingAsyncAsync method aync test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = asyncOnCompletedStub.pingAsyncAsync(responseObserver);
        requestObserver.onCompleted();
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:async-on-completed-propagation");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_METHOD_NAME_HEADER, "pingAsyncAsync");
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                
                from("grpc://localhost:" + GRPC_ASYNC_NEXT_REQUEST_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?consumerStrategy=PROPAGATION")
                    .to("mock:async-on-next-propagation")
                    .bean(new GrpcMessageBuilder(), "buildAsyncPongResponse");
                
                from("grpc://localhost:" + GRPC_ASYNC_COMPLETED_REQUEST_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?consumerStrategy=PROPAGATION&forwardOnCompleted=true")
                    .to("mock:async-on-completed-propagation");
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
            latch.countDown();
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
        
        public PongResponse buildAsyncPongResponse(PingRequest pingRequests) {
            return PongResponse.newBuilder().setPongName(pingRequests.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(pingRequests.getPingId()).build();
        }
    }
    
}
