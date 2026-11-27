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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.grpc.auth.jwt.JwtAlgorithm;
import org.apache.camel.component.grpc.auth.jwt.JwtCallCredentials;
import org.apache.camel.component.grpc.auth.jwt.JwtHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer checks the exp (expires at) and iat (issued at) claims of the JSON Web Token.
 */
public class GrpcConsumerJwtExpiryTest extends GrpcTestSupport {

    private static final String SECRET = "jwtexpirysecret";
    private static final String PING = "PING";
    private static final String PONG = "PONG";

    private ManagedChannel noMaxAgeChannel;
    private ManagedChannel maxAgeChannel;

    @BeforeEach
    public void startGrpcChannels() {
        noMaxAgeChannel = ManagedChannelBuilder.forAddress("localhost", getRoutePort("grpc-jwt-no-max-age"))
                .usePlaintext().build();
        maxAgeChannel = ManagedChannelBuilder.forAddress("localhost", getRoutePort("grpc-jwt-max-age"))
                .usePlaintext().build();
    }

    @AfterEach
    public void stopGrpcChannels() throws Exception {
        noMaxAgeChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        maxAgeChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testTokenWithoutExpiryIsAcceptedWithoutMaxAge() {
        String token = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, SECRET, null, null);

        assertEquals(PING + PONG, ping(noMaxAgeChannel, token).getPongName());
    }

    @Test
    public void testExpiredTokenIsRejected() {
        Instant now = Instant.now();
        String token = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, SECRET, null, null,
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)));

        assertUnauthenticated(noMaxAgeChannel, token);
        assertUnauthenticated(maxAgeChannel, token);
    }

    @Test
    public void testTokenWithinClockDifferenceIsAccepted() {
        Instant now = Instant.now();
        // issued "in the future" and expired a little ago, as seen from a host whose clock differs by some seconds
        String token = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, SECRET, null, null,
                now.plus(Duration.ofSeconds(20)), now.minus(Duration.ofSeconds(20)));

        assertEquals(PING + PONG, ping(noMaxAgeChannel, token).getPongName());
    }

    @Test
    public void testTokenOlderThanMaxAgeIsRejected() {
        Instant now = Instant.now();
        String token = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, SECRET, null, null,
                now.minus(Duration.ofHours(2)), now.plus(Duration.ofHours(1)));

        assertUnauthenticated(maxAgeChannel, token);
        // the same token is accepted by a consumer without jwtMaxTokenAge
        assertEquals(PING + PONG, ping(noMaxAgeChannel, token).getPongName());
    }

    @Test
    public void testTokenWithoutIssuedAtIsRejectedWithMaxAge() {
        String token = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, SECRET, null, null);

        assertUnauthenticated(maxAgeChannel, token);
    }

    @Test
    public void testRecentTokenIsAcceptedWithMaxAge() {
        Instant now = Instant.now();
        String token = JwtHelper.createJwtToken(JwtAlgorithm.HMAC256, SECRET, null, null,
                now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(50)));

        assertEquals(PING + PONG, ping(maxAgeChannel, token).getPongName());
    }

    @Test
    public void testProducerTokenIsAcceptedWithMaxAge() {
        Object reply = template.requestBody(producerUri(""), pingRequest());

        assertEquals(PING + PONG, assertInstanceOf(PongResponse.class, reply).getPongName());
    }

    @Test
    public void testProducerTokenWithoutExpiryIsRejectedWithMaxAge() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(producerUri("&jwtTokenLifetime=0"), pingRequest()));

        StatusRuntimeException cause = assertInstanceOf(StatusRuntimeException.class, e.getCause().getCause());
        assertEquals(Status.Code.UNAUTHENTICATED, cause.getStatus().getCode());
        assertTrue(cause.getMessage().contains("jwtMaxTokenAge"), cause.getMessage());
    }

    private String producerUri(String options) {
        return "grpc://localhost:" + getRoutePort("grpc-jwt-max-age") + "/org.apache.camel.component.grpc.PingPong"
               + "?method=pingSyncSync&synchronous=true&authenticationType=JWT&jwtSecret=" + SECRET + options;
    }

    private static PingRequest pingRequest() {
        return PingRequest.newBuilder().setPingName(PING).setPingId(1).build();
    }

    private static PongResponse ping(ManagedChannel channel, String token) {
        return PingPongGrpc.newBlockingStub(channel).withCallCredentials(new JwtCallCredentials(token))
                .pingSyncSync(pingRequest());
    }

    private static void assertUnauthenticated(ManagedChannel channel, String token) {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> ping(channel, token));
        assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=AGGREGATION&authenticationType=JWT&jwtSecret=" + SECRET)
                        .routeId("grpc-jwt-no-max-age")
                        .bean(new GrpcMessageBuilder(), "buildPongResponse");

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                     + "&consumerStrategy=AGGREGATION&authenticationType=JWT&jwtSecret=" + SECRET
                     + "&jwtMaxTokenAge=1h")
                        .routeId("grpc-jwt-max-age")
                        .bean(new GrpcMessageBuilder(), "buildPongResponse");

            }
        };
    }

    static class GrpcMessageBuilder {
        public PongResponse buildPongResponse(PingRequest pingRequest) {
            return PongResponse.newBuilder().setPongName(pingRequest.getPingName() + PONG)
                    .setPongId(pingRequest.getPingId()).build();
        }
    }
}
