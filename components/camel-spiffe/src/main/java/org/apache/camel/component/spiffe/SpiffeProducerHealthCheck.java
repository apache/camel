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
package org.apache.camel.component.spiffe;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.spiffe.bundle.jwtbundle.JwtBundleSet;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.Watcher;
import io.spiffe.workloadapi.WorkloadApiClient;
import io.spiffe.workloadapi.X509Context;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/** Readiness from SDK watch callbacks. Health calls only inspect immutable metadata and never contact the API. */
public final class SpiffeProducerHealthCheck extends AbstractHealthCheck implements AutoCloseable {
    private final boolean x509;
    private final long expiryThreshold;
    private final Clock clock;
    private final AtomicReference<Observation> observation
            = new AtomicReference<>(new Observation("not-started", false, null, null, null));

    public SpiffeProducerHealthCheck(String id, SpiffeOperation operation, long expiryThreshold) {
        this(id, operation, expiryThreshold, Clock.systemUTC());
    }

    SpiffeProducerHealthCheck(String id, SpiffeOperation operation, long expiryThreshold, Clock clock) {
        super("camel", "producer:spiffe-" + id);
        if (expiryThreshold < 0) {
            throw new IllegalArgumentException("healthCheckExpiryThreshold must not be negative");
        }
        this.x509 = operation == SpiffeOperation.fetchX509Svid;
        this.expiryThreshold = expiryThreshold;
        this.clock = clock;
    }

    void observe(WorkloadApiClient client) {
        observation.set(new Observation("connecting", false, null, null, null));
        if (x509) {
            client.watchX509Context(new Watcher<>() {
                @Override
                public void onUpdate(X509Context update) {
                    Instant validFrom = null;
                    Instant expiry = null;
                    try {
                        if (update != null) {
                            X509Svid svid = update.getDefaultSvid();
                            if (svid != null && svid.getLeaf() != null && svid.getLeaf().getNotAfter() != null) {
                                expiry = svid.getLeaf().getNotAfter().toInstant();
                                if (svid.getLeaf().getNotBefore() != null) {
                                    validFrom = svid.getLeaf().getNotBefore().toInstant();
                                }
                            }
                        }
                    } catch (RuntimeException e) {
                        // An empty/malformed update is unavailable material, not a reason to throw on an SDK thread.
                        validFrom = null;
                        expiry = null;
                    }
                    record(expiry != null, validFrom, expiry);
                }

                @Override
                public void onError(Throwable error) {
                    unavailable();
                }
            });
        } else {
            client.watchJwtBundles(new Watcher<>() {
                @Override
                public void onUpdate(JwtBundleSet update) {
                    record(update != null && update.getBundles() != null && !update.getBundles().isEmpty(), null, null);
                }

                @Override
                public void onError(Throwable error) {
                    unavailable();
                }
            });
        }
    }

    private void record(boolean material, Instant validFrom, Instant expiry) {
        Instant now = clock.instant();
        observation.updateAndGet(previous -> previous.connection().equals("stopped")
                ? previous
                : new Observation("connected", material, validFrom, expiry, now));
    }

    void unavailable() {
        // Exception text can contain request details. Retain no exception, credential, certificate or key material.
        observation.updateAndGet(previous -> previous.connection().equals("stopped")
                ? previous
                : new Observation(
                        "disconnected", previous.material(), previous.validFrom(), previous.expiry(), previous.lastUpdate()));
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Observation current = observation.get();
        Instant now = clock.instant();
        builder.detail("spiffe.observation", x509 ? "x509-identity" : "jwt-bundles");
        builder.detail("spiffe.connection", current.connection());
        builder.detail("spiffe.materialAvailable", current.material());
        if (current.lastUpdate() != null) {
            builder.detail("spiffe.lastUpdate", current.lastUpdate().toString());
        }
        boolean materialValid = current.material();
        boolean expiring = false;
        if (current.expiry() != null) {
            long remaining = Duration.between(now, current.expiry()).toMillis();
            materialValid = remaining > 0 && (current.validFrom() == null || !now.isBefore(current.validFrom()));
            expiring = remaining <= expiryThreshold;
            builder.detail("spiffe.expiry", current.expiry().toString());
            builder.detail("spiffe.remainingValidityMillis", remaining);
            builder.detail("spiffe.expiring", expiring);
        }
        if (x509) {
            builder.detail("spiffe.materialValid", materialValid);
        }
        if (!current.connection().equals("connected")) {
            builder.down();
            builder.message("SPIFFE Workload API observer is " + current.connection());
        } else if (!materialValid) {
            builder.down();
            builder.message("No usable SPIFFE " + (x509 ? "X.509 identity" : "JWT bundles") + " observed");
        } else if (expiring) {
            builder.down();
            builder.message("SPIFFE X.509 identity is within the configured expiry threshold");
        } else {
            builder.up();
        }
    }

    @Override
    public void close() {
        observation.set(new Observation("stopped", false, null, null, null));
    }

    private record Observation(String connection, boolean material, Instant validFrom, Instant expiry, Instant lastUpdate) {
    }
}
