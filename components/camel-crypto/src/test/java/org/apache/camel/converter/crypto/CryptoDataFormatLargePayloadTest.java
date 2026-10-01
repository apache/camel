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
package org.apache.camel.converter.crypto;

import java.util.Arrays;

import javax.crypto.KeyGenerator;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HMAC (appended by default) is split off in a circular buffer on unmarshal. The buffer must wrap around for any
 * message larger than the buffer size (4096 bytes by default).
 */
public class CryptoDataFormatLargePayloadTest extends CamelTestSupport {

    @Test
    void testRoundTripSmallerThanBuffer() throws Exception {
        doRoundTrip(100);
    }

    @Test
    void testRoundTripBufferSize() throws Exception {
        doRoundTrip(4096);
    }

    @Test
    void testRoundTripLargerThanBuffer() throws Exception {
        doRoundTrip(5000);
    }

    @Test
    void testRoundTripLarge() throws Exception {
        doRoundTrip(100_000);
    }

    @Test
    void testTamperedCiphertextLargerThanBufferFailsAuthentication() {
        byte[] encrypted = template.requestBody("direct:marshal", payload(5000), byte[].class);
        encrypted[encrypted.length / 2] ^= 0x01;

        assertAuthenticationFailed(encrypted);
    }

    @Test
    void testTruncatedCiphertextLargerThanBufferFailsAuthentication() {
        byte[] encrypted = template.requestBody("direct:marshal", payload(5000), byte[].class);
        // drop the last cipher block (DES has 8 byte blocks)
        byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - 8);

        // on this branch a truncated message fails on decryption (padding) before the mac is checked
        assertThrows(CamelExecutionException.class, () -> template.requestBody("direct:unmarshal", truncated));
    }

    private void assertAuthenticationFailed(byte[] encrypted) {
        CamelExecutionException e
                = assertThrows(CamelExecutionException.class, () -> template.requestBody("direct:unmarshal", encrypted));
        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("Expected mac did not match actual mac"), cause.getMessage());
    }

    private void doRoundTrip(int size) throws Exception {
        byte[] payload = payload(size);

        MockEndpoint mock = getMockEndpoint("mock:unencrypted");
        mock.expectedMessageCount(1);
        template.sendBody("direct:basic", payload);
        MockEndpoint.assertIsSatisfied(context);

        assertArrayEquals(payload, mock.getReceivedExchanges().get(0).getIn().getMandatoryBody(byte[].class));
    }

    private static byte[] payload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i * 31 + 7);
        }
        return payload;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                KeyGenerator generator = KeyGenerator.getInstance("DES");
                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES", generator.generateKey());

                from("direct:basic")
                        .marshal(cryptoFormat)
                        .unmarshal(cryptoFormat)
                        .to("mock:unencrypted");

                from("direct:marshal")
                        .marshal(cryptoFormat);

                from("direct:unmarshal")
                        .unmarshal(cryptoFormat);
            }
        };
    }
}
