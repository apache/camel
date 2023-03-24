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

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.OpenSslClientContext;
import io.netty.handler.ssl.SslContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.grpc.auth.jwt.JwtAlgorithm;
import org.apache.camel.component.grpc.auth.jwt.JwtCallCredentials;
import org.apache.camel.component.grpc.auth.jwt.JwtHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrpcConsumerSecurityTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumerSecurityTest.class);

    private static final int GRPC_TLS_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_JWT_CORRECT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_JWT_INCORRECT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";

    private static final String GRPC_JWT_CORRECT_SECRET = "correctsecret";
    private static final String GRPC_JWT_INCORRECT_SECRET = "incorrectsecret";

    private ManagedChannel tlsChannel;
    private ManagedChannel jwtCorrectChannel;
    private ManagedChannel jwtIncorrectChannel;

    private PingPongGrpc.PingPongStub tlsAsyncStub;
    private PingPongGrpc.PingPongStub jwtCorrectAsyncStub;
    private PingPongGrpc.PingPongStub jwtIncorrectAsyncStub;

    @BeforeEach
    public void startGrpcChannels() throws SSLException {
        String correctJwtToken = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, GRPC_JWT_CORRECT_SECRET, null, null);
        String incorrectJwtToken = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, GRPC_JWT_INCORRECT_SECRET, null, null);

        SslContext sslContext = GrpcSslContexts.forClient()
                .keyManager(new File("src/test/resources/certs/client.pem"), new File("src/test/resources/certs/client.key"))
                .trustManager(new File("src/test/resources/certs/ca.pem"))
                .build();

        Assumptions.assumeTrue(sslContext instanceof OpenSslClientContext || sslContext instanceof JdkSslContext);

        tlsChannel = NettyChannelBuilder.forAddress("localhost", GRPC_TLS_TEST_PORT)
                .sslContext(sslContext)
                .build();

        jwtCorrectChannel = NettyChannelBuilder.forAddress("localhost", GRPC_JWT_CORRECT_TEST_PORT).usePlaintext().build();
        jwtIncorrectChannel = NettyChannelBuilder.forAddress("localhost", GRPC_JWT_INCORRECT_TEST_PORT).usePlaintext().build();

        tlsAsyncStub = PingPongGrpc.newStub(tlsChannel);
        jwtCorrectAsyncStub
                = PingPongGrpc.newStub(jwtCorrectChannel).withCallCredentials(new JwtCallCredentials(correctJwtToken));
        jwtIncorrectAsyncStub
                = PingPongGrpc.newStub(jwtIncorrectChannel).withCallCredentials(new JwtCallCredentials(incorrectJwtToken));

    }

    @AfterEach
    public void stopGrpcChannels() throws Exception {
        if (tlsChannel != null) {
            tlsChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
        if (jwtCorrectChannel != null) {
            jwtCorrectChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
        if (jwtIncorrectChannel != null) {
            jwtIncorrectChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testWithEnableTLS() throws Exception {
        LOG.info("gRPC pingAsyncSync method async test with TLS enable start");

        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = tlsAsyncStub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest);
        requestObserver.onCompleted();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:tls-enable");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_METHOD_NAME_HEADER, "pingAsyncSync");
        mockEndpoint.assertIsSatisfied();

        PongResponse pongResponse = responseObserver.getPongResponse();
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testWithCorrectJWT() throws Exception {
        LOG.info("gRPC pingAsyncSync method async test with correct JWT start");

        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = jwtCorrectAsyncStub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest);
        requestObserver.onCompleted();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:jwt-correct-secret");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(GrpcConstants.GRPC_METHOD_NAME_HEADER, "pingAsyncSync");
        mockEndpoint.assertIsSatisfied();

        PongResponse pongResponse = responseObserver.getPongResponse();
        assertNotNull(pongResponse);
        assertEquals(GRPC_TEST_PING_ID, pongResponse.getPongId());
        assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());
    }

    @Test
    public void testWithIncorrectJWT() throws Exception {
        LOG.info("gRPC pingAsyncSync method async test with correct JWT start");

        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        StreamObserver<PingRequest> requestObserver = jwtIncorrectAsyncStub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest);
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:jwt-incorrect-secret");
        mockEndpoint.expectedMessageCount(0);
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("grpc://localhost:" + GRPC_TLS_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?consumerStrategy=PROPAGATION&"
                     + "negotiationType=TLS&keyCertChainResource=file:src/test/resources/certs/server.pem&"
                     + "keyResource=file:src/test/resources/certs/server.key&trustCertCollectionResource=file:src/test/resources/certs/ca.pem")
                        .to("mock:tls-enable")
                        .bean(new GrpcMessageBuilder(), "buildAsyncPongResponse");

                from("grpc://localhost:" + GRPC_JWT_CORRECT_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?consumerStrategy=PROPAGATION&"
                     + "authenticationType=JWT&jwtSecret=" + GRPC_JWT_CORRECT_SECRET)
                        .to("mock:jwt-correct-secret")
                        .bean(new GrpcMessageBuilder(), "buildAsyncPongResponse");

                from("grpc://localhost:" + GRPC_JWT_INCORRECT_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?consumerStrategy=PROPAGATION&"
                     + "authenticationType=JWT&jwtSecret=" + GRPC_JWT_CORRECT_SECRET)
                        .to("mock:jwt-incorrect-secret")
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
            return PongResponse.newBuilder().setPongName(pingRequests.getPingName() + GRPC_TEST_PONG_VALUE)
                    .setPongId(pingRequests.getPingId()).build();
        }
    }
}
