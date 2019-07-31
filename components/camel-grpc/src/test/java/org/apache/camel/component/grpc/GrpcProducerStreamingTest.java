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
import java.util.LinkedList;
import java.util.List;

import io.grpc.Server;
import io.grpc.ServerBuilder;
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

public class GrpcProducerStreamingTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducerStreamingTest.class);

    private static final int GRPC_TEST_PORT = AvailablePortFinder.getNextAvailable();

    private static Server grpcServer;
    private static PingPongImpl pingPongServer;

    @Before
    public void startGrpcServer() throws Exception {
        pingPongServer = new PingPongImpl();
        grpcServer = ServerBuilder.forPort(GRPC_TEST_PORT).addService(pingPongServer).build().start();
        LOG.info("gRPC server started on port {}", GRPC_TEST_PORT);
    }

    @After
    public void stopGrpcServer() throws IOException {
        if (grpcServer != null) {
            grpcServer.shutdown();
            LOG.info("gRPC server stopped");
            pingPongServer = null;
        }
    }

    @Test
    public void testPingAsyncAsync() throws Exception {
        int messageCount = 10;
        for (int i = 1; i <= messageCount; i++) {
            template.sendBody("direct:grpc-stream-async-async-route", PingRequest.newBuilder().setPingName(String.valueOf(i)).build());
        }

        MockEndpoint replies = getMockEndpoint("mock:grpc-replies");
        replies.expectedMessageCount(messageCount);
        replies.assertIsSatisfied();

        context().stop();

        assertNotNull(pingPongServer.getLastStreamRequests());
        assertListSize(pingPongServer.getLastStreamRequests(), 1);
        assertListSize(pingPongServer.getLastStreamRequests().get(0), messageCount);
    }

    @Test
    public void testPingAsyncAsyncRecovery() throws Exception {
        int messageGroupCount = 5;
        for (int i = 1; i <= messageGroupCount; i++) {
            template.sendBody("direct:grpc-stream-async-async-route", PingRequest.newBuilder().setPingName(String.valueOf(i)).build());
        }

        template.sendBody("direct:grpc-stream-async-async-route", PingRequest.newBuilder().setPingName("error").build());

        MockEndpoint replies = getMockEndpoint("mock:grpc-replies");
        replies.expectedMessageCount(messageGroupCount);
        replies.assertIsSatisfied();

        Thread.sleep(2000);

        for (int i = messageGroupCount + 1; i <= 2 * messageGroupCount; i++) {
            template.sendBody("direct:grpc-stream-async-async-route", PingRequest.newBuilder().setPingName(String.valueOf(i)).build());
        }

        replies.reset();
        replies.expectedMessageCount(messageGroupCount);
        replies.assertIsSatisfied();

        context().stop();

        assertNotNull(pingPongServer.getLastStreamRequests());
        assertListSize(pingPongServer.getLastStreamRequests(), 2);
        assertListSize(pingPongServer.getLastStreamRequests().get(0), messageGroupCount + 1);
        assertListSize(pingPongServer.getLastStreamRequests().get(1), messageGroupCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:grpc-stream-async-async-route")
                    .to("grpc://localhost:" + GRPC_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?producerStrategy=STREAMING&streamRepliesTo=direct:grpc-replies&method=pingAsyncAsync");

                from("direct:grpc-replies")
                    .to("mock:grpc-replies");
            }
        };
    }

    /**
     * Test gRPC PingPong server implementation
     */
    static class PingPongImpl extends PingPongGrpc.PingPongImplBase {

        private List<List<PingRequest>> streamRequests = new LinkedList<>();

        @Override
        public StreamObserver<PingRequest> pingAsyncAsync(StreamObserver<PongResponse> responseObserver) {
            StreamObserver<PingRequest> requestObserver = new StreamObserver<PingRequest>() {

                private List<PingRequest> streamRequests = new LinkedList<>();

                @Override
                public void onNext(PingRequest request) {
                    streamRequests.add(request);
                    if ("error".equals(request.getPingName())) {
                        PingPongImpl.this.streamRequests.add(streamRequests);
                        responseObserver.onError(new RuntimeException("Requested error"));
                    } else {
                        PongResponse response = PongResponse.newBuilder().setPongName("Hello " + request.getPingName()).build();
                        responseObserver.onNext(response);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    PingPongImpl.this.streamRequests.add(streamRequests);
                    LOG.info("Error in pingAsyncAsync() {}", t.getMessage());
                }

                @Override
                public void onCompleted() {
                    PingPongImpl.this.streamRequests.add(streamRequests);
                    responseObserver.onCompleted();
                }
            };
            return requestObserver;
        }

        public List<List<PingRequest>> getLastStreamRequests() {
            return streamRequests;
        }

    }
}
