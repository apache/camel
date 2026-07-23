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
package org.apache.camel.component.a2a.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.OAuthTokenValidationResult;

/**
 * Authenticated A2A caller profile returned by security scheme handlers.
 */
public final class A2AUserProfile {

    private final String scheme;
    private final String subject;
    private final String issuer;
    private final List<String> audience;
    private final List<String> scopes;
    private final Map<String, Object> claims;

    private A2AUserProfile(
                           String scheme,
                           String subject,
                           String issuer,
                           List<String> audience,
                           List<String> scopes,
                           Map<String, Object> claims) {
        this.scheme = scheme;
        this.subject = subject;
        this.issuer = issuer;
        this.audience = copyList(audience);
        this.scopes = copyList(scopes);
        this.claims = copyMap(claims);
    }

    public static A2AUserProfile forScheme(String scheme) {
        return new A2AUserProfile(scheme, null, null, null, null, null);
    }

    public static A2AUserProfile fromOAuthResult(String scheme, OAuthTokenValidationResult result) {
        return new A2AUserProfile(
                scheme,
                result.getSubject(),
                result.getIssuer(),
                result.getAudience(),
                result.getScopes(),
                result.getClaims());
    }

    public static A2AUserProfile fromMap(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return null;
        }
        return new A2AUserProfile(
                stringValue(profile.get("scheme")),
                stringValue(profile.get("subject")),
                stringValue(profile.get("issuer")),
                stringList(profile.get("audience")),
                stringList(profile.get("scopes")),
                objectMap(profile.get("claims")));
    }

    public String getScheme() {
        return scheme;
    }

    public String getSubject() {
        return subject;
    }

    public String getIssuer() {
        return issuer;
    }

    public List<String> getAudience() {
        return audience;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "scheme", scheme);
        putIfPresent(map, "subject", subject);
        putIfPresent(map, "issuer", issuer);
        if (!audience.isEmpty()) {
            map.put("audience", audience);
        }
        if (!scopes.isEmpty()) {
            map.put("scopes", scopes);
        }
        if (!claims.isEmpty()) {
            map.put("claims", claims);
        }
        return Collections.unmodifiableMap(map);
    }

    public String ownerKey() {
        if (issuer != null || subject != null) {
            return String.valueOf(issuer) + "|" + String.valueOf(subject);
        }
        Map<String, Object> profile = asMap();
        return profile.isEmpty() ? null : profile.toString();
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> answer = new ArrayList<>(list.size());
            for (Object item : list) {
                answer.add(String.valueOf(item));
            }
            return answer;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
