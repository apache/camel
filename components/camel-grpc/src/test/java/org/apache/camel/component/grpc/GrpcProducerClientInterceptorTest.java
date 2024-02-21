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

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GrpcProducerClientInterceptorTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducerClientInterceptorTest.class);

    private static final int GRPC_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private static Server grpcServer;
    private final GrpcMockClientInterceptor mockClientInterceptor = mock(GrpcMockClientInterceptor.class);
    private final GrpcMockClientInterceptor mockClientInterceptor2 = mock(GrpcMockClientInterceptor.class);

    @BeforeAll
    public static void startGrpcServer() throws Exception {
        grpcServer = ServerBuilder.forPort(GRPC_TEST_PORT).addService(new GrpcProducerClientInterceptorTest.PingPongImpl())
                .build().start();
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
    public void testClientInterceptors() {
        when(mockClientInterceptor.interceptCall(any(), any(), any())).thenCallRealMethod();
        when(mockClientInterceptor2.interceptCall(any(), any(), any())).thenCallRealMethod();
        LOG.info("gRPC PingSyncSync method test start");
        // Testing simple sync method invoke with host and port parameters
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        template.requestBody("direct:grpc-interceptor", pingRequest);
        verify(mockClientInterceptor, times(1)).interceptCall(any(), any(), any());
        verify(mockClientInterceptor2, times(1)).interceptCall(any(), any(), any());
    }

    @Test
    public void testNoAutoDiscover() throws Exception {
        GrpcComponent component = context.getComponent("grpc", GrpcComponent.class);
        GrpcEndpoint endpoint = (GrpcEndpoint) component.createEndpoint("grpc://localhost:" + GRPC_TEST_PORT
                                                                        + "/org.apache.camel.component.grpc"
                                                                        + ".PingPong?method=pingSyncSync&autoDiscoverClientInterceptors=false");

        assertFalse(endpoint.getConfiguration().isAutoDiscoverClientInterceptors());
        assertEquals(0, endpoint.getConfiguration().getClientInterceptors().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // This needs to be here because if it's any earlier the context won't be set up and if it's any later, we'll
        // put it in the registry after the component is already set up.
        context.getRegistry().bind("grpcMockClientInterceptor", mockClientInterceptor);
        context.getRegistry().bind("grpcMockClientInterceptor2", mockClientInterceptor2);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:grpc-interceptor")
                        .to("grpc://localhost:" + GRPC_TEST_PORT
                            + "/org.apache.camel.component.grpc"
                            + ".PingPong?method=pingSyncSync");
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
    }

}
