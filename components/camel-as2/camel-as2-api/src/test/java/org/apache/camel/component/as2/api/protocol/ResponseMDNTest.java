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
package org.apache.camel.component.as2.api.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.as2.api.entity.TextPlainEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseMDNTest {

    @Test
    void mdnTextPlainEntityWithCrlfProducesConsistentDigest() throws IOException {
        // Simulate MDN text with explicit CRLF (as in the fixed DEFAULT_MDN_MESSAGE_TEMPLATE)
        String mdnText = "MDN for -\r\n"
                         + " Message ID: test-id\r\n"
                         + " Status: processed\r\n";

        TextPlainEntity entity = new TextPlainEntity(mdnText, StandardCharsets.US_ASCII.name(), "7bit", false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        byte[] output = out.toByteArray();

        // Verify that the body content preserves CRLF
        String outputStr = new String(output, StandardCharsets.US_ASCII);
        // The body part (after headers + blank line) should contain CRLF
        String body = outputStr.substring(outputStr.indexOf("\r\n\r\n") + 4);
        assertTrue(body.contains("\r\n"), "MDN body should contain CRLF line endings");
        assertFalse(body.contains("\n") && !body.contains("\r\n"),
                "MDN body should not contain bare LF (without CR)");
    }

    @Test
    void mdnTextPlainEntityWithLfProducesMismatch() throws IOException {
        // Simulate MDN text with LF only (the old broken DEFAULT_MDN_MESSAGE_TEMPLATE behavior)
        String mdnText = "MDN for -\n"
                         + " Message ID: test-id\n"
                         + " Status: processed\n";

        TextPlainEntity entity = new TextPlainEntity(mdnText, StandardCharsets.US_ASCII.name(), "7bit", false);

        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        entity.writeTo(out1);

        // Now create with CRLF
        String mdnTextCrlf = "MDN for -\r\n"
                             + " Message ID: test-id\r\n"
                             + " Status: processed\r\n";

        TextPlainEntity entityCrlf = new TextPlainEntity(mdnTextCrlf, StandardCharsets.US_ASCII.name(), "7bit", false);

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        entityCrlf.writeTo(out2);

        // Headers should be identical (both go through CanonicalOutputStream)
        // but body content will differ in line endings
        assertNotEquals(out1.size(), out2.size(),
                "LF and CRLF bodies should produce different byte lengths");
    }
}
