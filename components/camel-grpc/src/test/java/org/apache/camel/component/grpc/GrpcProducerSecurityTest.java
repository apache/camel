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
import java.io.IOException;

import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslProvider;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.grpc.auth.jwt.JwtAlgorithm;
import org.apache.camel.component.grpc.auth.jwt.JwtServerInterceptor;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcProducerSecurityTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducerSecurityTest.class);

    private static final int GRPC_TLS_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_JWT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final int GRPC_TEST_PONG_ID01 = 1;
    private static final int GRPC_TEST_PONG_ID02 = 2;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";
    
    private static final String GRPC_JWT_CORRECT_SECRET = "correctsecret";
    private static final String GRPC_JWT_INCORRECT_SECRET = "incorrectsecret";
    
    private static Server grpcServerWithTLS;
    private static Server grpcServerWithJWT;
    
    @BeforeClass
    public static void startGrpcServer() throws Exception {
        grpcServerWithTLS = NettyServerBuilder.forPort(GRPC_TLS_TEST_PORT)
                                              .sslContext(GrpcSslContexts.forServer(new File("src/test/resources/certs/server.pem"),
                                                                                    new File("src/test/resources/certs/server.key"))
                                                                         .trustManager(new File("src/test/resources/certs/ca.pem"))
                                                                         .clientAuth(ClientAuth.REQUIRE)
                                                                         .sslProvider(SslProvider.OPENSSL)
                                                                         .build())
                                              .addService(new PingPongImpl()).build().start();
        
        grpcServerWithJWT = NettyServerBuilder.forPort(GRPC_JWT_TEST_PORT)
                                              .addService(new PingPongImpl())
                                              .intercept(new JwtServerInterceptor(JwtAlgorithm.HMAC256, GRPC_JWT_CORRECT_SECRET, null, null))
                                              .build()
                                              .start();
        
        LOG.info("gRPC server with TLS started on port {}", GRPC_TLS_TEST_PORT);
        LOG.info("gRPC server with the JWT auth started on port {}", GRPC_JWT_TEST_PORT);
    }

    @AfterClass
    public static void stopGrpcServer() throws IOException {
        if (grpcServerWithTLS != null) {
            grpcServerWithTLS.shutdown();
            LOG.info("gRPC server with TLS stoped");
        }
        
        if (grpcServerWithJWT != null) {
            grpcServerWithJWT.shutdown();
            LOG.info("gRPC server with JWT stoped");
        }
    }

    @Test
    public void testWithEnableTLS() throws Exception {
        LOG.info("gRPC PingSyncSync method test start with TLS enable");
        // Testing simple sync method invoke using TLS negotiation
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        Object pongResponse = template.requestBody("direct:grpc-tls", pingRequest);
        
        assertNotNull(pongResponse);
        assertTrue(pongResponse instanceof PongResponse);
        assertEquals(((PongResponse)pongResponse).getPongId(), GRPC_TEST_PING_ID);
        assertEquals(((PongResponse)pongResponse).getPongName(), GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE);
    }
    
    @Test
    public void testWithCorrectJWT() throws Exception {
        LOG.info("gRPC PingSyncSync method test start with correct JWT authentication");
        // Testing simple sync method invoke using correct JWT authentication
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        Object pongResponse = template.requestBody("direct:grpc-correct-jwt", pingRequest);
        
        assertNotNull(pongResponse);
        assertTrue(pongResponse instanceof PongResponse);
        assertEquals(((PongResponse)pongResponse).getPongId(), GRPC_TEST_PING_ID);
        assertEquals(((PongResponse)pongResponse).getPongName(), GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE);
    }
    
    @Test
    public void testWithIncorrectJWT() throws Exception {
        LOG.info("gRPC PingSyncSync method test start with incorrect JWT authentication");
        // Testing simple sync method invoke using incorrect JWT authentication
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        
        try {
            template.requestBody("direct:grpc-incorrect-jwt", pingRequest);
        } catch (Exception e) {
            assertNotNull(e);
            assertTrue(e.getCause().getCause() instanceof StatusRuntimeException);
            assertEquals(e.getCause().getCause().getMessage(), "UNAUTHENTICATED: The Token's Signature resulted invalid when verified using the Algorithm: HmacSHA256");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:grpc-tls").to("grpc://localhost:" + GRPC_TLS_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync&synchronous=true&"
                     + "negotiationType=TLS&keyCertChainResource=file:src/test/resources/certs/client.pem&" 
                     + "keyResource=file:src/test/resources/certs/client.key&trustCertCollectionResource=file:src/test/resources/certs/ca.pem");
                
                from("direct:grpc-correct-jwt").to("grpc://localhost:" + GRPC_JWT_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync&synchronous=true&"
                    + "authenticationType=JWT&jwtSecret=" + GRPC_JWT_CORRECT_SECRET);
                
                from("direct:grpc-incorrect-jwt").to("grpc://localhost:" + GRPC_JWT_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync&synchronous=true&"
                    + "authenticationType=JWT&jwtSecret=" + GRPC_JWT_INCORRECT_SECRET);
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
    }
}
