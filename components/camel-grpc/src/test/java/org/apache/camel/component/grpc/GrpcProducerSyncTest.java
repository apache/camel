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

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrpcProducerSyncTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducerSyncTest.class);

    private static final int GRPC_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final int GRPC_TEST_PONG_ID01 = 1;
    private static final int GRPC_TEST_PONG_ID02 = 2;
    private static final int MULTIPLE_RUN_TEST_COUNT = 100;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private static Server grpcServer;

    @BeforeAll
    public static void startGrpcServer() throws Exception {
        grpcServer = ServerBuilder.forPort(GRPC_TEST_PORT).addService(new PingPongImpl()).build().start();
        LOG.info("gRPC server started on port {}", GRPC_TEST_PORT);
    }

    @AfterAll
    public static void stopGrpcServer() {
        if (grpcServer != null) {
            grpcServer.shutdown();
            LOG.info("gRPC server stopped");
        }
    }

    @Test
    public void testPingSyncSyncMethodInvocation() {
        LOG.info("gRPC PingSyncSync method test start");
        // Testing simple sync method invoke with host and port parameters
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        Object pongResponse = template.requestBody("direct:grpc-sync-sync", pingRequest);
        assertNotNull(pongResponse);
        assertTrue(pongResponse instanceof PongResponse);
        assertEquals(GRPC_TEST_PING_ID, ((PongResponse) pongResponse).getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, ((PongResponse) pongResponse).getPongName());

        // Testing simple sync method with name described in .proto file instead
        // of generated class
        pongResponse = template.requestBody("direct:grpc-sync-proto-method-name", pingRequest);
        assertNotNull(pongResponse);
        assertTrue(pongResponse instanceof PongResponse);
        assertEquals(GRPC_TEST_PING_ID, ((PongResponse) pongResponse).getPongId());
    }

    @Test
    public void testPingSyncSyncMultipleInvocation() {
        final StopWatch stopwatch = new StopWatch();
        // Multiple sync methods call for average performance estimation
        for (int id = 0; id < MULTIPLE_RUN_TEST_COUNT; id++) {
            PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE + id).setPingId(id).build();
            Object pongResponse = template.requestBody("direct:grpc-sync-sync", pingRequest);
            assertEquals(((PongResponse) pongResponse).getPongId(), id);
        }
        LOG.info("Multiple sync invocation time {} milliseconds, average operations/sec {}",
                stopwatch.taken(),
                Math.round(1000 * MULTIPLE_RUN_TEST_COUNT / stopwatch.taken()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPingSyncAsyncMethodInvocation() {
        LOG.info("gRPC PingSyncAsync method test start");
        // Testing simple method with sync request and asyc response in synchronous invocation style
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        Object pongResponse = template.requestBody("direct:grpc-sync-async", pingRequest);
        assertNotNull(pongResponse);
        assertTrue(pongResponse instanceof List<?>);
        assertEquals(GRPC_TEST_PONG_ID01, ((List<PongResponse>) pongResponse).get(0).getPongId());
        assertEquals(GRPC_TEST_PONG_ID02, ((List<PongResponse>) pongResponse).get(1).getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, ((List<PongResponse>) pongResponse).get(0).getPongName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:grpc-sync-sync")
                        .to("grpc://localhost:" + GRPC_TEST_PORT
                            + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync&synchronous=true");
                from("direct:grpc-sync-proto-method-name")
                        .to("grpc://localhost:" + GRPC_TEST_PORT
                            + "/org.apache.camel.component.grpc.PingPong?method=PingSyncSync&synchronous=true");
                from("direct:grpc-sync-async")
                        .to("grpc://localhost:" + GRPC_TEST_PORT
                            + "/org.apache.camel.component.grpc.PingPong?method=pingSyncAsync&synchronous=true");
            }
        };
    }

    /**
     * Test gRPC PingPong server implementation
     */
    static class PingPongImpl extends PingPongGrpc.PingPongImplBase {
        @Override
        public void pingSyncSync(PingRequest request, StreamObserver<PongResponse> responseObserver) {
            LOG.info("gRPC server received data from PingPong service PingId={} PingName={}", request.getPingId(),
                    request.getPingName());
            PongResponse response = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(request.getPingId()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void pingSyncAsync(PingRequest request, StreamObserver<PongResponse> responseObserver) {
            LOG.info("gRPC server received data from PingAsyncResponse service PingId={} PingName={}", request.getPingId(),
                    request.getPingName());
            PongResponse response01 = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(GRPC_TEST_PONG_ID01).build();
            PongResponse response02 = PongResponse.newBuilder().setPongName(request.getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(GRPC_TEST_PONG_ID02).build();
            responseObserver.onNext(response01);
            responseObserver.onNext(response02);
            responseObserver.onCompleted();
        }
    }
}
