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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GrpcConsumerServerInterceptorTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumerServerInterceptorTest.class);

    private static final int GRPC_REQUEST_INTERCEPT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_REQUEST_NO_INTERCEPT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private static Server grpcServer;
    private final GrpcMockServerInterceptor mockServerInterceptor = mock(GrpcMockServerInterceptor.class);
    private final GrpcMockServerInterceptor mockServerInterceptor2 = mock(GrpcMockServerInterceptor.class);

    private ManagedChannel interceptRequestChannel;
    private ManagedChannel nointerceptRequestChannel;
    private PingPongGrpc.PingPongBlockingStub interceptBlockingStub;
    private PingPongGrpc.PingPongBlockingStub nointerceptBlockingStub;

    @BeforeEach
    public void startGrpcChannels() {
        interceptRequestChannel
                = ManagedChannelBuilder.forAddress("localhost", GRPC_REQUEST_INTERCEPT_TEST_PORT).usePlaintext().build();
        nointerceptRequestChannel
                = ManagedChannelBuilder.forAddress("localhost", GRPC_REQUEST_NO_INTERCEPT_TEST_PORT).usePlaintext().build();
        interceptBlockingStub = PingPongGrpc.newBlockingStub(interceptRequestChannel);
        nointerceptBlockingStub = PingPongGrpc.newBlockingStub(nointerceptRequestChannel);
    }

    @AfterEach
    public void stopGrpcChannels() {
        interceptRequestChannel.shutdown().shutdownNow();
        nointerceptRequestChannel.shutdown().shutdownNow();
    }

    @Test
    public void testServerInterceptors() throws Exception {
        when(mockServerInterceptor.interceptCall(any(), any(), any())).thenCallRealMethod();
        when(mockServerInterceptor2.interceptCall(any(), any(), any())).thenCallRealMethod();
        LOG.info("gRPC pingSyncSync method blocking test start");
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponse pongResponse = interceptBlockingStub.pingSyncSync(pingRequest);

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
        verify(mockServerInterceptor, times(1)).interceptCall(any(), any(), any());
        verify(mockServerInterceptor2, times(1)).interceptCall(any(), any(), any());
    }

    @Test
    public void testNoAutoDiscover() throws Exception {
        when(mockServerInterceptor.interceptCall(any(), any(), any())).thenCallRealMethod();
        when(mockServerInterceptor2.interceptCall(any(), any(), any())).thenCallRealMethod();
        LOG.info("gRPC pingSyncSync method blocking test start");
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponse pongResponse = nointerceptBlockingStub.pingSyncSync(pingRequest);

        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
        verify(mockServerInterceptor, times(0)).interceptCall(any(), any(), any());
        verify(mockServerInterceptor2, times(0)).interceptCall(any(), any(), any());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        context.getRegistry().bind("grpcMockServerInterceptor", mockServerInterceptor);
        context.getRegistry().bind("grpcMockServerInterceptor2", mockServerInterceptor2);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:" + GRPC_REQUEST_INTERCEPT_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION")
                        .bean(new GrpcMessageBuilder(), "buildPongResponse");

                from("grpc://localhost:" + GRPC_REQUEST_NO_INTERCEPT_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION"
                     + "&autoDiscoverServerInterceptors=false")
                        .bean(new GrpcMessageBuilder(), "buildPongResponse");
            }
        };
    }

    static class GrpcMessageBuilder {
        public PongResponse buildPongResponse(PingRequest pingRequest) {
            return PongResponse.newBuilder().setPongName(pingRequest.getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(pingRequest.getPingId()).build();
        }

        public PongResponse buildAsyncPongResponse(List<PingRequest> pingRequests) {
            return PongResponse.newBuilder().setPongName(pingRequests.get(0).getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(pingRequests.get(0).getPingId()).build();
        }
    }
}
