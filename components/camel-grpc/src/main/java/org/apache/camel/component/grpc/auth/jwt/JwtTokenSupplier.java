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
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * Supplies the JSON Web Token that the producer sends, with the iat (issued at) and exp (expires at) claims. A new
 * token is created when less than a tenth of the lifetime, at most one minute, is left of the current one. The check is
 * a volatile read and a clock read per call; only the call that finds the token due creates a new one.
 */
public class JwtTokenSupplier implements Supplier<String> {

    /**
     * The longest time before the expiry of a token at which a new token is created.
     */
    static final Duration MAX_REFRESH_AHEAD = Duration.ofMinutes(1);

    private final JwtAlgorithm algorithm;
    private final String secret;
    private final String issuer;
    private final String subject;
    private final Duration lifetime;
    private final Clock clock;

    private volatile Token current;

    /**
     * @param lifetime how long a token is valid, 0 or less for a token without iat and exp claims, which never expires
     */
    public JwtTokenSupplier(JwtAlgorithm algorithm, String secret, String issuer, String subject, Duration lifetime) {
        this(algorithm, secret, issuer, subject, lifetime, Clock.systemUTC());
    }

    public JwtTokenSupplier(JwtAlgorithm algorithm, String secret, String issuer, String subject, Duration lifetime,
                            Clock clock) {
        if (!lifetime.isNegative() && !lifetime.isZero() && lifetime.compareTo(Duration.ofSeconds(1)) < 0) {
            // the iat and exp claims are in seconds
            throw new IllegalArgumentException(
                    "jwtTokenLifetime must be 0 (no expiry) or at least 1 second, was " + lifetime.toMillis() + " millis");
        }
        this.algorithm = algorithm;
        this.secret = secret;
        this.issuer = issuer;
        this.subject = subject;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    @Override
    public String get() {
        Token token = current;
        if (token == null || token.isDue(clock.instant())) {
            synchronized (this) {
                token = current;
                if (token == null || token.isDue(clock.instant())) {
                    token = createToken();
                    current = token;
                }
            }
        }
        return token.value;
    }

    private Token createToken() {
        if (lifetime.isNegative() || lifetime.isZero()) {
            return new Token(JwtHelper.createJwtToken(algorithm, secret, issuer, subject), Instant.MAX);
        }
        // the claims are in seconds, so compute the refresh time from the truncated values that the token carries
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(lifetime).truncatedTo(ChronoUnit.SECONDS);
        Duration ahead = lifetime.dividedBy(10);
        if (ahead.compareTo(MAX_REFRESH_AHEAD) > 0) {
            ahead = MAX_REFRESH_AHEAD;
        }
        String value = JwtHelper.createJwtToken(algorithm, secret, issuer, subject, issuedAt, expiresAt);
        return new Token(value, expiresAt.minus(ahead));
    }

    private record Token(String value, Instant refreshAt) {
        boolean isDue(Instant now) {
            return !now.isBefore(refreshAt);
        }
    }
}
