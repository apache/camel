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
package org.apache.camel.component.cm.test;

import org.apache.camel.component.cm.CMConstants;
import org.apache.camel.component.cm.CMMessage;
import org.apache.camel.component.cm.CMUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every character of the GSM 03.38 default alphabet and of its extension table (see
 * https://unicode.org/Public/MAPPINGS/ETSI/GSM0338.TXT) can be sent in a GSM message.
 */
class CMGsm0338Test {

    // the basic character set without ESC (0x1B), in the order of the table
    private static final String GSM_BASIC = "@£$¥èéùìòÇ\nØø\rÅå"
                                            + "Δ_ΦΓΛΩΠΨΣΘΞÆæßÉ"
                                            + " !\"#¤%&'()*+,-./0123456789:;<=>?"
                                            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
                                            + "¿abcdefghijklmnopqrstuvwxyzäöñüà";

    // the extension table: form feed, ^ { } \ [ ~ ] | and the euro sign
    private static final String GSM_EXTENSION = "\f^{}\\[~]|€";

    @Test
    void everyBasicCharacterIsGsm() {
        assertEquals("", notGsm(GSM_BASIC), "characters of the GSM 03.38 basic character set reported as not GSM");
    }

    @Test
    void everyExtensionCharacterIsGsm() {
        assertEquals("", notGsm(GSM_EXTENSION), "characters of the GSM 03.38 extension table reported as not GSM");
    }

    @Test
    void textWithAngleBracketsIsSentAsGsm() {
        // 310 characters: 3 GSM parts of 153 characters, but 5 unicode parts of 67 characters
        String text = "if a < b and b > c then a < c. ".repeat(10);

        CMMessage message = new CMMessage("+34612345678", text);
        message.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertFalse(message.isUnicode(), "Should not be unicode");
        assertEquals(3, message.getMultiparts());
    }

    private static String notGsm(String chars) {
        StringBuilder sb = new StringBuilder();
        for (char c : chars.toCharArray()) {
            if (!CMUtils.isGsm0338Encodeable(String.valueOf(c))) {
                sb.append(String.format("U+%04X ", (int) c));
            }
        }
        return sb.toString().trim();
    }
}
