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
package org.apache.camel.oauth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserProfile {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<String, Object> attributes;
    private final Map<String, Object> principal;

    public UserProfile(Map<String, Object> attributes, Map<String, Object> principal) {
        this.attributes = new LinkedHashMap<>(attributes);
        this.principal = new LinkedHashMap<>(principal);
    }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Map<String, Object> principal() {
        return Collections.unmodifiableMap(principal);
    }

    public long ttl() {
        long now = System.currentTimeMillis() / 1000L;
        var ttl = Optional.ofNullable(attributes.get("exp"))
                .map(Object::toString)
                .map(s -> Long.parseLong(s) - now)
                .orElse(0L);
        return ttl;
    }

    @SuppressWarnings("unchecked")
    public String subject() {
        if (principal().containsKey("username")) {
            return principal().get("username").toString();
        } else if (principal().containsKey("userHandle")) {
            return principal().get("userHandle").toString();
        } else {
            if (attributes().containsKey("idToken")) {
                var idToken = (Map<String, Object>) attributes().get("idToken");
                if (idToken.containsKey("sub")) {
                    return idToken.get("sub").toString();
                }
            }
            return getClaim("sub");
        }
    }

    public Optional<String> accessToken() {
        return Optional.ofNullable(principal.get("access_token")).map(Object::toString);
    }

    public Optional<String> idToken() {
        return Optional.ofNullable(principal.get("id_token")).map(Object::toString);
    }

    public Optional<String> refreshToken() {
        return Optional.ofNullable(principal.get("refresh_token")).map(Object::toString);
    }

    @SuppressWarnings("unchecked")
    public <T> T getClaim(String key) {
        var maybeRootClaim = Optional.ofNullable(attributes().get("rootClaim"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast);
        if (maybeRootClaim.isPresent()) {
            var rootClaim = maybeRootClaim.get();
            if (rootClaim.containsKey(key)) {
                return (T) rootClaim.get(key);
            }
        }
        if (attributes().containsKey(key)) {
            return (T) attributes().get(key);
        } else {
            return (T) (principal().get(key));
        }
    }

    public void merge(UserProfile other) {
        attributes.putAll(other.attributes);
        principal.putAll(other.principal);
    }

    public void logDetails(String prefix) {
        log.info("{}: {}", prefix, subject());
        log.debug("User Attributes ...");
        attributes().forEach((k, v) -> log.debug("   {}: {}", k, v));
        log.debug("User Principal ...");
        principal().forEach((k, v) -> log.debug("   {}: {}", k, v));
    }
}
