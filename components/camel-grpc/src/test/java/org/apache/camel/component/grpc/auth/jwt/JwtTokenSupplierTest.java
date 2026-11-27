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
package org.apache.camel.component.grpc.auth.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenSupplierTest {

    private static final String SECRET = "supersecuredsecret";
    private static final Instant START = Instant.parse("2026-11-27T10:00:00Z");

    @Test
    void tokenHasIssuedAtAndExpiresAt() {
        MutableClock clock = new MutableClock(START);
        JwtTokenSupplier supplier = new JwtTokenSupplier(
                JwtAlgorithm.HMAC256, SECRET, "issuer", "subject", Duration.ofHours(1), clock);

        DecodedJWT token = JWT.decode(supplier.get());

        assertEquals(START, token.getIssuedAtAsInstant());
        assertEquals(START.plus(Duration.ofHours(1)), token.getExpiresAtAsInstant());
        assertEquals("issuer", token.getIssuer());
        assertEquals("subject", token.getSubject());
    }

    @Test
    void tokenIsRefreshedOneMinuteBeforeExpiry() {
        MutableClock clock = new MutableClock(START);
        JwtTokenSupplier supplier = new JwtTokenSupplier(
                JwtAlgorithm.HMAC256, SECRET, null, null, Duration.ofHours(1), clock);

        String first = supplier.get();
        clock.set(START.plus(Duration.ofMinutes(58)));
        assertSame(first, supplier.get());

        // one minute before the expiry, a new token is created
        clock.set(START.plus(Duration.ofMinutes(59)));
        String second = supplier.get();
        assertNotEquals(first, second);
        DecodedJWT token = JWT.decode(second);
        assertEquals(START.plus(Duration.ofMinutes(59)), token.getIssuedAtAsInstant());
        assertEquals(START.plus(Duration.ofMinutes(119)), token.getExpiresAtAsInstant());
        assertSame(second, supplier.get());
    }

    @Test
    void shortTokenIsRefreshedWhenATenthOfTheLifetimeIsLeft() {
        MutableClock clock = new MutableClock(START);
        JwtTokenSupplier supplier = new JwtTokenSupplier(
                JwtAlgorithm.HMAC256, SECRET, null, null, Duration.ofSeconds(10), clock);

        String first = supplier.get();
        clock.set(START.plus(Duration.ofMillis(8999)));
        assertSame(first, supplier.get());

        clock.set(START.plus(Duration.ofSeconds(9)));
        DecodedJWT token = JWT.decode(supplier.get());
        assertEquals(START.plus(Duration.ofSeconds(9)), token.getIssuedAtAsInstant());
        assertEquals(START.plus(Duration.ofSeconds(19)), token.getExpiresAtAsInstant());
    }

    @Test
    void zeroLifetimeCreatesTokenWithoutExpiry() {
        MutableClock clock = new MutableClock(START);
        JwtTokenSupplier supplier = new JwtTokenSupplier(
                JwtAlgorithm.HMAC256, SECRET, null, null, Duration.ZERO, clock);

        String first = supplier.get();
        DecodedJWT token = JWT.decode(first);
        assertNull(token.getIssuedAt());
        assertNull(token.getExpiresAt());

        clock.set(START.plus(Duration.ofDays(365)));
        assertSame(first, supplier.get());
    }

    @Test
    void lifetimeBelowOneSecondIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenSupplier(JwtAlgorithm.HMAC256, SECRET, null, null, Duration.ofMillis(500)));
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
