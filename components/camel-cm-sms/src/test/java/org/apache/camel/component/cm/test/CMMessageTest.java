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

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import org.apache.camel.component.cm.CMConstants;
import org.apache.camel.component.cm.CMMessage;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CamelSpringTest
@ContextConfiguration(classes = { ValidatorConfiguration.class })
public class CMMessageTest {

    private final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();
    private String validNumber;

    @BeforeEach
    public void beforeTest() {
        validNumber = pnu.format(pnu.getExampleNumber("ES"), PhoneNumberFormat.E164);
    }

    /*
     * GSM0338
     */

    @Test
    public void testGSM338AndLTMAXGSMMESSAGELENGTH() {

        // 0338 and less than 160 char -> 1 part

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < CMConstants.MAX_GSM_MESSAGE_LENGTH; index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(1, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 1");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndEQMAXGSMMESSAGELENGTH() {
        // 0338 and length is exactly 160 -> 1 part

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < CMConstants.MAX_GSM_MESSAGE_LENGTH; index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(1, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 1");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndGTMAXGSMMESSAGELENGTH() {

        // 0338 and length is exactly 161 -> 2 part

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < CMConstants.MAX_GSM_MESSAGE_LENGTH + 1; index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(2, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 2");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndLT2MAXGSMMESSAGELENGTH() {
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (2 * CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART - 1); index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(2, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 2");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndEQ2MAXGSMMESSAGELENGTH() {

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (2 * CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART); index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(2, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 2");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndGT2MAXGSMMESSAGELENGTH() {
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (2 * CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART + 1); index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(3, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 3");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndEQ8MAXGSMMESSAGELENGTH() {
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (8 * CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART); index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(8, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 8");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    @Test
    public void testGSM338AndGT8MAXGSMMESSAGELENGTH() {

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (8 * CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART + 1); index++) {
            sb.append("a");
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(8, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 8");
        assertFalse(cmMessage.isUnicode(), "Should not be unicode");
    }

    /*
     * Unicode Messages
     */

    @Test
    public void testUnicodeAndLTMAXGSMMESSAGELENGTH() {

        String ch = "\uF400";

        // 0338 and less than 160 char -> 1 part

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < CMConstants.MAX_UNICODE_MESSAGE_LENGTH; index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(1, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 1");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndEQMAXGSMMESSAGELENGTH() {
        // 0338 and length is exactly 160 -> 1 part

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < CMConstants.MAX_UNICODE_MESSAGE_LENGTH; index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(1, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 1");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndGTMAXGSMMESSAGELENGTH() {

        // 0338 and length is exactly 161 -> 2 part

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < CMConstants.MAX_UNICODE_MESSAGE_LENGTH + 1; index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(2, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 2");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndLT2MAXGSMMESSAGELENGTH() {

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (2 * CMConstants.MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART - 1); index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(2, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 2");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndEQ2MAXGSMMESSAGELENGTH() {

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (2 * CMConstants.MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART); index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(2, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 2");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndGT2MAXGSMMESSAGELENGTH() {

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (2 * CMConstants.MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART + 1); index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(3, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 3");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndEQ8MAXGSMMESSAGELENGTH() {

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (8 * CMConstants.MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART); index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(8, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 8");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

    @Test
    public void testUnicodeAndGT8MAXGSMMESSAGELENGTH() {

        String ch = "\uF400";

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < (8 * CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART + 1); index++) {
            sb.append(ch);
        }

        final CMMessage cmMessage = new CMMessage(validNumber, sb.toString());
        cmMessage.setUnicodeAndMultipart(CMConstants.DEFAULT_MULTIPARTS);

        assertEquals(8, cmMessage.getMultiparts(), "Call to getMultiparts() should have returned 8");
        assertTrue(cmMessage.isUnicode(), "Should have been unicode");
    }

}
