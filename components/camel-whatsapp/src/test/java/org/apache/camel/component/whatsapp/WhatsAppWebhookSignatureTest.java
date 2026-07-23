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
package org.apache.camel.component.whatsapp;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppWebhookSignatureTest {

    private static final String SECRET = "my-app-secret";

    private final byte[] payload = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void validSignatureAccepted() throws Exception {
        assertTrue(WhatsAppWebhookProcessor.isValidSignature(payload, sign(payload, SECRET), SECRET));
    }

    @Test
    void invalidSignatureRejected() {
        assertFalse(WhatsAppWebhookProcessor.isValidSignature(payload, "sha256=deadbeef", SECRET));
    }

    @Test
    void missingSignatureRejected() {
        assertFalse(WhatsAppWebhookProcessor.isValidSignature(payload, null, SECRET));
    }

    @Test
    void tamperedPayloadRejected() throws Exception {
        String signature = sign(payload, SECRET);
        byte[] tampered = "{\"object\":\"evil\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(WhatsAppWebhookProcessor.isValidSignature(tampered, signature, SECRET));
    }

    @Test
    void wrongSecretRejected() throws Exception {
        String signature = sign(payload, "another-secret");
        assertFalse(WhatsAppWebhookProcessor.isValidSignature(payload, signature, SECRET));
    }

    private static String sign(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
