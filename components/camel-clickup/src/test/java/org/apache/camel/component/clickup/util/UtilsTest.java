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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The webhook signature has to be reproducible on any JVM: ClickUp computes it over the raw UTF-8 request body, so the
 * expected values below are HMAC-SHA256 over UTF-8 bytes.
 */
public class UtilsTest {

    @Test
    public void shouldComputeTheHmacOfAnAsciiMessage() {
        assertEquals("B82FCB791ACEC57859B989B430A826488CE2E479FDF92326BD0A2E8375A42BA4",
                Utils.computeMessageHMAC("payload", "secret"));
    }

    @Test
    public void shouldComputeTheHmacOverUtf8Bytes() {
        // "sëcret" and "pãyload": neither is representable in US-ASCII, and both encode differently in ISO-8859-1
        final String key = "sëcret";
        final String message = "pãyload";

        assertEquals("3F449438582E13522059F2FD70C99DDF4BEB084815B88E184F487E3F0F627656",
                Utils.computeMessageHMAC(message, key));
    }

}
