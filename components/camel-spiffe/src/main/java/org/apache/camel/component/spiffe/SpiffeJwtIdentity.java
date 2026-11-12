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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.spiffe.svid.jwtsvid.JwtSvid;

/**
 * A snapshot of a validated JWT-SVID's identity and claims, without the bearer token. Claims may still contain
 * application-sensitive information; they are deliberately omitted from {@link #toString()}.
 */
public final class SpiffeJwtIdentity {
    private final String spiffeId;
    private final Instant expiry;
    private final Set<String> audience;
    private final Map<String, Object> claims;

    SpiffeJwtIdentity(JwtSvid svid) {
        spiffeId = svid.getSpiffeId().toString();
        expiry = svid.getExpiry() != null ? svid.getExpiry().toInstant() : null;
        audience = svid.getAudience() != null ? Set.copyOf(svid.getAudience()) : Set.of();
        claims = svid.getClaims() != null ? copyClaims(svid.getClaims()) : Map.of();
    }

    public String getSpiffeId() {
        return spiffeId;
    }

    public Instant getExpiry() {
        return expiry;
    }

    public Set<String> getAudience() {
        return audience;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    private static Map<String, Object> copyClaims(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, snapshot(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object snapshot(Object value) {
        if (value instanceof Map<?, ?> values) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            values.forEach((key, item) -> copy.put(key, snapshot(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> values) {
            List<Object> copy = new ArrayList<>(values.size());
            values.forEach(item -> copy.add(snapshot(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value;
    }

    @Override
    public String toString() {
        return "SpiffeJwtIdentity[spiffeId=" + spiffeId + ", expiry=" + expiry + "]";
    }
}
