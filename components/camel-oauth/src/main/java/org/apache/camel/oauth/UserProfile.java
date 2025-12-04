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

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserProfile {

    static final Logger log = LoggerFactory.getLogger(UserProfile.class);

    private final JsonObject principal;
    private final JsonObject attributes;

    public UserProfile(JsonObject principal, JsonObject attributes) {
        this.principal = principal;
        this.attributes = attributes;
    }

    public static UserProfile fromJson(OAuthConfig config, JsonObject json) {
        var principal = json.deepCopy();
        var attributes = new JsonObject();
        long now = System.currentTimeMillis() / 1000L;
        if (principal.has("expires_in")) {
            var expiresIn = json.get("expires_in").getAsLong();
            attributes.addProperty("exp", now + expiresIn);
            attributes.addProperty("iat", now);
        }
        if (principal.has("access_token")) {
            var accessToken = json.get("access_token").getAsString();
            var tokenJwt = verifyToken(config, accessToken, false);
            attributes.add("accessToken", tokenJwt);
            copyProperties(tokenJwt, attributes, true, "exp", "iat", "nbf", "sub");
            attributes.add("rootClaim", new JsonPrimitive("accessToken"));
        }
        if (principal.has("id_token")) {
            var idToken = json.get("id_token").getAsString();
            var tokenJwt = verifyToken(config, idToken, true);
            attributes.add("idToken", tokenJwt);
            copyProperties(tokenJwt, attributes, false, "sub", "name", "email", "picture");
            copyProperties(tokenJwt, principal, true, "amr");
        }
        return new UserProfile(principal, attributes);
    }

    public Map<String, Object> attributes() {
        return new Gson().fromJson(attributes, Map.class);
    }

    public Map<String, Object> principal() {
        return new Gson().fromJson(principal, Map.class);
    }

    public boolean expired() {
        return ttl() <= 0;
    }

    public long ttl() {
        long now = System.currentTimeMillis() / 1000L;
        var ttl = Optional.ofNullable(attributes.get("exp"))
                .map(Object::toString)
                .map(s -> Long.parseLong(s) - now)
                .orElse(0L);
        return ttl;
    }

    public String subject() {
        if (principal.has("username")) {
            return principal.get("username").getAsString();
        } else if (principal.has("userHandle")) {
            return principal.get("userHandle").getAsString();
        } else {
            if (attributes.has("idToken")) {
                var idToken = attributes.get("idToken").getAsJsonObject();
                if (idToken.has("sub")) {
                    return idToken.get("sub").getAsString();
                }
            }
            var maybeSub = Optional.ofNullable(getClaim("sub"));
            return maybeSub.map(JsonElement::getAsString).orElse(null);
        }
    }

    public Optional<String> accessToken() {
        var accessToken = principal.get("access_token");
        return Optional.ofNullable(accessToken).map(JsonElement::getAsString);
    }

    public Optional<String> idToken() {
        var idToken = principal.get("id_token");
        return Optional.ofNullable(idToken).map(JsonElement::getAsString);
    }

    public Optional<String> refreshToken() {
        var refreshToken = principal.get("refresh_token");
        return Optional.ofNullable(refreshToken).map(JsonElement::getAsString);
    }

    public JsonElement getClaim(String key) {
        var maybeRootClaim = Optional.ofNullable(attributes.get("rootClaim"))
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast);
        if (maybeRootClaim.isPresent()) {
            var rootClaim = maybeRootClaim.get();
            if (rootClaim.has(key)) {
                return rootClaim.get(key);
            }
        }
        if (attributes.has(key)) {
            return attributes.get(key);
        } else {
            return principal.get(key);
        }
    }

    public void merge(UserProfile other) {
        other.principal.entrySet().forEach((en) -> {
            principal.add(en.getKey(), en.getValue());
        });
        other.attributes.entrySet().forEach((en) -> {
            attributes.add(en.getKey(), en.getValue());
        });
    }

    public void logDetails() {
        log.debug("User Attributes ...");
        attributes().forEach((k, v) -> log.debug("   {}: {}", k, v));
        log.debug("User Principal ...");
        principal().forEach((k, v) -> log.debug("   {}: {}", k, v));
    }

    private static JsonObject verifyToken(OAuthConfig config, String token, boolean idToken) throws OAuthException {
        JsonObject tokenJwt;
        try {
            var signedJWT = SignedJWT.parse(token);
            var keyID = signedJWT.getHeader().getKeyID();

            // Fetch Keycloak public key
            var jwkSet = config.getJWKSet();
            if (!jwkSet.isEmpty()) {
                var rsaKey = (RSAKey) jwkSet.getKeyByKeyId(keyID);
                if (rsaKey == null) {
                    throw new OAuthException("No matching key found for: " + keyID);
                }
                RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
                if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                    throw new OAuthException("Invalid token signature");
                }
            }

            // Decode the payload into a JsonObject
            var payload = signedJWT.getPayload().toString();
            tokenJwt = JsonParser.parseString(payload).getAsJsonObject();

            var targetAudience = new ArrayList<String>();
            var jwtOptions = config.getJWTOptions();
            if (tokenJwt.has("aud")) {
                try {
                    var aud = tokenJwt.get("aud");
                    if (aud.isJsonPrimitive()) {
                        targetAudience.add(aud.getAsString());
                    } else {
                        targetAudience.addAll(aud.getAsJsonArray().asList().stream()
                                .map(JsonElement::getAsString)
                                .toList());
                    }
                } catch (RuntimeException ex) {
                    throw new OAuthException("User audience isn't a JsonArray or String");
                }
            }
            String clientId = config.getClientId();
            if (idToken && !targetAudience.contains(clientId)) {
                throw new OAuthException("Invalid JWT audience. Expected to contain: " + clientId);
            }
            if (idToken && tokenJwt.has("azp")) {
                String authorizedParty = tokenJwt.get("azp").getAsString();
                if (!authorizedParty.equals(clientId)) {
                    throw new OAuthException("Invalid authorized party != config.clientID");
                }
                if (!targetAudience.contains(authorizedParty)) {
                    throw new OAuthException("ID Token audience, doesn't contain authorized party");
                }
            }
            var issuer = jwtOptions.getIssuer();
            if (issuer != null && !issuer.equals(tokenJwt.get("iss").getAsString())) {
                throw new OAuthException("Invalid JWT issuer");
            }
        } catch (OAuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OAuthException("Invalid token", ex);
        }
        return tokenJwt;
    }

    private static void copyProperties(JsonObject source, JsonObject target, boolean overwrite, String... keys) {
        if (keys.length > 0) {
            for (String key : keys) {
                if (source.has(key) && (!target.has(key) || overwrite)) {
                    target.add(key, source.get(key));
                }
            }
        } else {
            for (var en : source.entrySet()) {
                if (!target.has(en.getKey()) || overwrite) {
                    target.add(en.getKey(), en.getValue());
                }
            }
        }
    }
}
