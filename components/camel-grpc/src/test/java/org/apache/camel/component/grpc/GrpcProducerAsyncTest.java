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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcProducerAsyncTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducerAsyncTest.class);

    private static final int GRPC_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final int GRPC_TEST_PONG_ID01 = 1;
    private static final int GRPC_TEST_PONG_ID02 = 2;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private static Server grpcServer;
    private Object asyncPongResponse;

    @BeforeClass
    public static void startGrpcServer() throws Exception {
        grpcServer = ServerBuilder.forPort(GRPC_TEST_PORT).addService(new PingPongImpl()).build().start();
        LOG.info("gRPC server started on port {}", GRPC_TEST_PORT);
    }

    @AfterClass
    public static void stopGrpcServer() throws IOException {
        if (grpcServer != null) {
            grpcServer.shutdown();
            LOG.info("gRPC server stoped");
        }
    }

    @Test
    public void testPingSyncSyncMethodInvocation() throws Exception {
        LOG.info("gRPC PingSyncSync method test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();

        // Testing sync service call with async style invocation
        template.asyncCallbackSendBody("direct:grpc-sync-sync", pingRequest, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                asyncPongResponse = exchange.getOut().getBody();
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);

        assertNotNull(asyncPongResponse);
        assertTrue(asyncPongResponse instanceof List);

        @SuppressWarnings("unchecked")
        List<PongResponse> asyncPongResponseList = (List<PongResponse>)asyncPongResponse;
        assertEquals(1, asyncPongResponseList.size());
        assertEquals(asyncPongResponseList.get(0).getPongId(), GRPC_TEST_PING_ID);
        assertEquals(asyncPongResponseList.get(0).getPongName(), GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE);
    }

    @Test
    public void testPingSyncAsyncMethodInvocation() throws Exception {
        LOG.info("gRPC PingSyncAsync method test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();

        // Testing async service call
        template.asyncCallbackSendBody("direct:grpc-sync-async", pingRequest, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                asyncPongResponse = exchange.getOut().getBody();
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);

        assertNotNull(asyncPongResponse);
        assertTrue(asyncPongResponse instanceof List);

        @SuppressWarnings("unchecked")
        List<PongResponse> asyncPongResponseList = (List<PongResponse>)asyncPongResponse;
        assertEquals(2, asyncPongResponseList.size());
        assertEquals(asyncPongResponseList.get(0).getPongId(), GRPC_TEST_PONG_ID01);
        assertEquals(asyncPongResponseList.get(1).getPongId(), GRPC_TEST_PONG_ID02);
        assertEquals(asyncPongResponseList.get(0).getPongName(), GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE);
    }

    @Test
    public void testPingAsyncSyncMethodInvocation() throws Exception {
        LOG.info("gRPC PingAsyncSync method test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();

        // Testing async service call with async style invocation
        template.asyncCallbackSendBody("direct:grpc-async-sync", pingRequest, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                asyncPongResponse = exchange.getOut().getBody();
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);

        assertNotNull(asyncPongResponse);
        assertTrue(asyncPongResponse instanceof List);

        @SuppressWarnings("unchecked")
        List<PongResponse> asyncPongResponseList = (List<PongResponse>)asyncPongResponse;
        assertEquals(1, asyncPongResponseList.size());
        assertEquals(asyncPongResponseList.get(0).getPongId(), GRPC_TEST_PING_ID);
        assertEquals(asyncPongResponseList.get(0).getPongName(), GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE);
    }

    @Test
    public void testPingAsyncAsyncMethodInvocation() throws Exception {
        LOG.info("gRPC PingAsyncAsync method test start");
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();

        // Testing async service call with async style invocation
        template.asyncCallbackSendBody("direct:grpc-async-async", pingRequest, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                asyncPongResponse = exchange.getOut().getBody();
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);

        assertNotNull(asyncPongResponse);
        assertTrue(asyncPongResponse instanceof List);

        @SuppressWarnings("unchecked")
        List<PongResponse> asyncPongResponseList = (List<PongResponse>)asyncPongResponse;
        assertEquals(1, asyncPongResponseList.size());
        assertEquals(asyncPongResponseList.get(0).getPongId(), GRPC_TEST_PING_ID);
        assertEquals(asyncPongResponseList.get(0).getPongName(), GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:grpc-sync-sync").to("grpc://localhost:" + GRPC_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync");
                from("direct:grpc-sync-async").to("grpc://localhost:" + GRPC_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingSyncAsync");
                from("direct:grpc-async-sync").to("grpc://localhost:" + GRPC_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingAsyncSync");
                from("direct:grpc-async-async").to("grpc://localhost:" + GRPC_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingAsyncAsync");
            }
        };
    }

    /**
     * Test gRPC PingPong server implementation
     */
    static class PingPongImpl extends PingPongGrpc.PingPongImplBase {
        @Override
        public void pingSyncSync(PingRequest request, StreamObserver<PongResponse> responseObserver) {
            LOG.info("gRPC server received data from PingPong service PingId={} PingName={}", request.getPingId(), request.getPingName());
            PongResponse response = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(request.getPingId()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void pingSyncAsync(PingRequest request, StreamObserver<PongResponse> responseObserver) {
            LOG.info("gRPC server received data from PingAsyncResponse service PingId={} PingName={}", request.getPingId(), request.getPingName());
            PongResponse response01 = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(GRPC_TEST_PONG_ID01).build();
            PongResponse response02 = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(GRPC_TEST_PONG_ID02).build();
            responseObserver.onNext(response01);
            responseObserver.onNext(response02);
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<PingRequest> pingAsyncSync(StreamObserver<PongResponse> responseObserver) {
            StreamObserver<PingRequest> requestObserver = new StreamObserver<PingRequest>() {

                @Override
                public void onNext(PingRequest request) {
                    PongResponse response = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(request.getPingId()).build();
                    responseObserver.onNext(response);
                }

                @Override
                public void onError(Throwable t) {
                    LOG.info("Error in pingAsyncSync() " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
            return requestObserver;
        }

        @Override
        public StreamObserver<PingRequest> pingAsyncAsync(StreamObserver<PongResponse> responseObserver) {
            StreamObserver<PingRequest> requestObserver = new StreamObserver<PingRequest>() {

                @Override
                public void onNext(PingRequest request) {
                    PongResponse response = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE).setPongId(request.getPingId()).build();
                    responseObserver.onNext(response);
                }

                @Override
                public void onError(Throwable t) {
                    LOG.info("Error in pingAsyncAsync() " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
            return requestObserver;
        }
    }
}
