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
package org.apache.camel.component.clickup.util;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The webhook signature has to be reproducible on any JVM: ClickUp computes it over the raw request body, so the
 * expected values below are HMAC-SHA256 over UTF-8 bytes.
 */
public class UtilsTest {

    @Test
    public void shouldComputeTheHmacOfAnAsciiMessage() {
        assertThat(Utils.computeMessageHMAC("payload", "secret"))
                .isEqualTo("B82FCB791ACEC57859B989B430A826488CE2E479FDF92326BD0A2E8375A42BA4");
    }

    @Test
    public void shouldComputeTheHmacOverUtf8Bytes() {
        // "secret" and "payload" with a diaeresis and a tilde: neither is representable in US-ASCII, and both
        // encode differently in ISO-8859-1
        final String key = "s\u00ebcret";
        final String message = "p\u00e3yload";

        assertThat(Utils.computeMessageHMAC(message, key))
                .isEqualTo("3F449438582E13522059F2FD70C99DDF4BEB084815B88E184F487E3F0F627656");
    }

    @Test
    public void shouldHashTheRawBytesAsGiven() {
        final String key = "s\u00ebcret";
        final String message = "p\u00e3yload";

        // the byte[] overload is what the webhook check uses, so for valid UTF-8 it has to agree with the String one
        assertThat(Utils.computeMessageHMAC(message.getBytes(StandardCharsets.UTF_8), key))
                .isEqualTo(Utils.computeMessageHMAC(message, key));
    }

    @Test
    public void shouldHashABodyThatIsNotValidUtf8() {
        final String key = "secret";
        // a lone 0x80 continuation byte: decoding the body to a String first turns it into U+FFFD and changes the
        // hash, which is why the check has to work on the bytes as they arrived
        final byte[] message = { 'a', (byte) 0x80, 'b' };

        assertThat(Utils.computeMessageHMAC(message, key))
                .isNotEqualTo(Utils.computeMessageHMAC(new String(message, StandardCharsets.UTF_8), key));
    }

}
