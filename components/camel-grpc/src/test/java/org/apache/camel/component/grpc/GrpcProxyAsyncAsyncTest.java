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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class GrpcProxyAsyncAsyncTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcProxyAsyncAsyncTest.class);

    private static final int GRPC_STUB_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_ROUTE_PORT = AvailablePortFinder.getNextAvailable();

    private static Server grpcServer;
    private ManagedChannel channel;
    private PingPongGrpc.PingPongStub stub;
    private final AtomicBoolean routeHasException = new AtomicBoolean(false);

    @BeforeAll
    public static void beforeAll() throws Exception {
        grpcServer = ServerBuilder.forPort(GRPC_STUB_PORT).addService(new PingPongImpl()).build().start();
        LOG.info("gRPC server started on port {}", GRPC_STUB_PORT);
    }

    @AfterAll
    public static void afterAll() {
        if (grpcServer != null) {
            grpcServer.shutdown();
            LOG.info("gRPC server stopped");
        }
    }

    @BeforeEach
    public void beforeEach() {
        channel = ManagedChannelBuilder.forAddress("localhost", GRPC_ROUTE_PORT).usePlaintext().build();
        stub = PingPongGrpc.newStub(channel);
    }

    @AfterEach
    public void afterEach() throws Exception {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void asyncAsyncTest() throws Exception {
        PingRequest rq1 = PingRequest.newBuilder().setPingName("rq1").setPingId(100).build();
        PingRequest rq2 = PingRequest.newBuilder().setPingName("rq2").setPingId(200).build();
        List<PongResponse> responses = new ArrayList<>();
        AtomicBoolean onCompleted = new AtomicBoolean(false);
        AtomicBoolean onError = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        var requests = stub.pingAsyncAsync(new StreamObserver<PongResponse>() {
            @Override
            public void onNext(PongResponse pongResponse) {
                responses.add(pongResponse);
            }

            @Override
            public void onError(Throwable throwable) {
                onError.set(true);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                onCompleted.set(true);
                latch.countDown();
            }
        });
        requests.onNext(rq1);
        requests.onNext(rq2);
        requests.onNext(rq2);
        requests.onCompleted();
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(routeHasException.get());
        assertEquals(6, responses.size());
        assertTrue(onCompleted.get());
        assertFalse(onError.get());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).process(e -> routeHasException.set(true));
                from("grpc://localhost:" + GRPC_ROUTE_PORT +
                     "/org.apache.camel.component.grpc.PingPong" +
                     "?routeControlledStreamObserver=true" +
                     "&consumerStrategy=DELEGATION" +
                     "&forwardOnError=true" +
                     "&forwardOnCompleted=true")
                        .toD("grpc://localhost:" + GRPC_STUB_PORT +
                             "/org.apache.camel.component.grpc.PingPong" +
                             "?method=${header.CamelGrpcMethodName}" +
                             "&producerStrategy=STREAMING" +
                             "&streamRepliesTo=direct:next" +
                             "&forwardOnError=true" +
                             "&forwardOnCompleted=true" +
                             "&inheritExchangePropertiesForReplies=true");
                from("direct:next")
                        .to("grpc://dummy:80/?toRouteControlledStreamObserver=true");
            }
        };
    }

    static class PingPongImpl extends PingPongGrpc.PingPongImplBase {

        @Override
        public StreamObserver<PingRequest> pingAsyncAsync(StreamObserver<PongResponse> responseObserver) {
            return new StreamObserver<PingRequest>() {
                @Override
                public void onNext(PingRequest pingRequest) {
                    var rs = PongResponse.newBuilder()
                            .setPongId(pingRequest.getPingId())
                            .setPongName(pingRequest.getPingName() + "-rs")
                            .build();
                    responseObserver.onNext(rs);
                    responseObserver.onNext(rs);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
    }
}
