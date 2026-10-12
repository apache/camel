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
package org.apache.camel.service.lra;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs the participant callback URLs enlisted with the LRA coordinator, and verifies the signature when the
 * coordinator calls them back, using HMAC-SHA256 with the callback secret of the LRA saga service.
 * <p>
 * The signature covers the callback path and every query parameter of the URL: the step endpoint URIs and the saga
 * options. Parameters are sorted and encoded individually, so the signature does not depend on the order or the
 * encoding used by the coordinator when it calls the URL back.
 */
final class LRACallbackSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    LRACallbackSigner(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * Computes the signature of a callback URL.
     *
     * @param  callbackPath the participant path the URL points to (compensate or complete)
     * @param  parameters   the query parameters of the URL, without the signature
     * @return              the signature, Base64 URL-safe encoded without padding
     */
    String sign(String callbackPath, Map<String, String> parameters) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(canonicalForm(callbackPath, parameters).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot compute the signature of the LRA participant callback URL", e);
        }
    }

    /**
     * Checks the signature of a callback URL, in constant time.
     *
     * @param  callbackPath the participant path that was called (compensate or complete)
     * @param  parameters   the query parameters of the called URL, without the signature
     * @param  signature    the signature received with the URL, may be <tt>null</tt>
     * @return              true if the signature matches the path and the parameters
     */
    boolean verify(String callbackPath, Map<String, String> parameters, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(callbackPath, parameters).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalForm(String callbackPath, Map<String, String> parameters) {
        StringJoiner joiner = new StringJoiner("&", callbackPath + "?", "");
        new TreeMap<>(parameters).forEach((name, value) -> joiner.add(encode(name) + "=" + encode(value)));
        return joiner.toString();
    }

    private static String encode(String value) {
        // a null option value is sent as an empty parameter value, so it is signed as one
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
