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

import javax.net.ssl.SSLHandshakeException;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A producer calling a TLS server that does not ask for a client certificate: the client only verifies the server, so
 * no client key is configured.
 */
class GrpcProducerServerTlsTest extends CamelTestSupport {

    private static final String PING_NAME = "PING";

    private static Server grpcServer;

    @BeforeAll
    static void startGrpcServer() throws Exception {
        SslContext sslContext = GrpcSslContexts
                .forServer(new File("src/test/resources/certs/server.pem"), new File("src/test/resources/certs/server.key"))
                .clientAuth(ClientAuth.NONE)
                .build();

        grpcServer = NettyServerBuilder.forPort(0)
                .sslContext(sslContext)
                .addService(new PingPongImpl())
                .build()
                .start();
    }

    @AfterAll
    static void stopGrpcServer() {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    void tlsWithTrustedServerCertificateOnly() {
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(PING_NAME).setPingId(1).build();

        Object pongResponse = template.requestBody("direct:grpc-tls", pingRequest);

        PongResponse pong = assertInstanceOf(PongResponse.class, pongResponse);
        assertEquals(1, pong.getPongId());
        assertEquals(PING_NAME + "PONG", pong.getPongName());
    }

    @Test
    void tlsWithDefaultTrustRejectsUnknownServerCertificate() {
        // without trustCertCollectionResource the default trust store of the JVM is used, which does not trust
        // the test CA, so the TLS handshake fails (the server certificate is verified, not trusted blindly)
        PingRequest pingRequest = PingRequest.newBuilder().setPingName(PING_NAME).setPingId(1).build();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:grpc-tls-default-trust", pingRequest));

        StatusRuntimeException status = ObjectHelper.getException(StatusRuntimeException.class, e);
        assertNotNull(status, "Expected a StatusRuntimeException in the cause chain");
        assertEquals(Status.Code.UNAVAILABLE, status.getStatus().getCode());
        assertNotNull(ObjectHelper.getException(SSLHandshakeException.class, e),
                "Expected the TLS handshake to fail as the server certificate is not trusted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:grpc-tls")
                        .to("grpc://localhost:" + grpcServer.getPort()
                            + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync&synchronous=true"
                            + "&negotiationType=TLS&trustCertCollectionResource=file:src/test/resources/certs/ca.pem");

                from("direct:grpc-tls-default-trust")
                        .to("grpc://localhost:" + grpcServer.getPort()
                            + "/org.apache.camel.component.grpc.PingPong?method=pingSyncSync&synchronous=true"
                            + "&negotiationType=TLS");
            }
        };
    }

    static class PingPongImpl extends PingPongGrpc.PingPongImplBase {
        @Override
        public void pingSyncSync(PingRequest request, StreamObserver<PongResponse> responseObserver) {
            responseObserver.onNext(PongResponse.newBuilder().setPongName(request.getPingName() + "PONG")
                    .setPongId(request.getPingId()).build());
            responseObserver.onCompleted();
        }
    }
}
