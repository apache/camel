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
package org.apache.camel.component.as2.api;

import org.apache.camel.component.as2.api.entity.MultipartMimeEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delivery address for an asynchronous MDN comes from the {@code Receipt-Delivery-Option} header of the received
 * AS2 message, so it is chosen by the sender. It selects an outbound destination and, before this was constrained, the
 * configured MDN credentials went with it.
 */
class AS2AsynchronousMDNManagerDeliveryAddressTest {

    private static final String ALLOWED = "partner.example";

    @Test
    void aSchemeOtherThanHttpIsRefused() {
        for (String address : new String[] {
                "file:///etc/passwd", "ftp://partner.example/x", "gopher://partner.example:70/x", "//partner.example/x" }) {
            HttpException e = assertThrows(HttpException.class, () -> deliver(address, ALLOWED),
                    "expected " + address + " to be refused");
            assertTrue(e.getMessage().contains("http or https") || e.getMessage().contains("no host"),
                    "unexpected message for " + address + ": " + e.getMessage());
        }
    }

    @Test
    void aHostOutsideTheAllowListIsRefused() {
        HttpException e = assertThrows(HttpException.class,
                () -> deliver("http://attacker.example/receipts", ALLOWED));
        assertTrue(e.getMessage().contains("asyncMdnAllowedHosts"), "unexpected message: " + e.getMessage());
    }

    /**
     * With no allow-list the MDN is still delivered, so this gets past the address checks and fails on the connection
     * instead - which is what tells us the address itself was accepted.
     */
    @Test
    void withNoAllowListTheAddressIsStillAccepted() {
        Exception e = assertThrows(Exception.class,
                () -> deliver("http://localhost:1/receipts", null));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnAllowedHosts"),
                "the address must not be refused when no allow-list is configured: " + e.getMessage());
    }

    private static void deliver(String deliveryAddress, String allowedHosts) throws Exception {
        AS2AsynchronousMDNManager manager = new AS2AsynchronousMDNManager(
                "1.1", "Camel", "sender.example.com", null, null, "user", "password", null, allowedHosts);
        manager.send(new TestEntity(), AS2MimeType.MULTIPART_REPORT, deliveryAddress);
    }

    /**
     * The manager null-checks its entity before it looks at the delivery address, so the address checks need a real
     * one. Its content is irrelevant here - none of these cases reach the point of writing it.
     */
    private static final class TestEntity extends MultipartMimeEntity {

        private TestEntity() {
            super(ContentType.create(AS2MimeType.MULTIPART_REPORT), "7bit");
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
