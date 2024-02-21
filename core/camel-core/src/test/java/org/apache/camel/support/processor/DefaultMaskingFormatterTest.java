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
package org.apache.camel.support.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultMaskingFormatterTest {

    @Test
    public void testDefaultOption() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        String answer
                = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'");
        assertEquals("key=value, myPassword=xxxxx,\n myPassphrase=\"xxxxx\", secretKey='xxxxx'", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        assertEquals("<xmlPassword>\n xxxxx \n</xmlPassword>\n<user password=\"xxxxx\"/>", answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"Password\":\"foo\", \"Passphrase\" : \"foo bar\", \"SecretKey\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"Password\":\"xxxxx\", \"Passphrase\" : \"xxxxx\", \"SecretKey\" : \"xxxxx\"}",
                answer);
    }

    @Test
    public void testDisableKeyValueMask() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(false, true, true);
        String answer
                = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'");
        assertEquals("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        assertEquals("<xmlPassword>\n xxxxx \n</xmlPassword>\n<user password=\"asdf qwert\"/>", answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"Password\":\"foo\", \"Passphrase\" : \"foo bar\", \"SecretKey\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"Password\":\"xxxxx\", \"Passphrase\" : \"xxxxx\", \"SecretKey\" : \"xxxxx\"}",
                answer);
    }

    @Test
    public void testDisableXmlElementMask() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(true, false, true);
        String answer
                = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'");
        assertEquals("key=value, myPassword=xxxxx,\n myPassphrase=\"xxxxx\", secretKey='xxxxx'", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        assertEquals("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"xxxxx\"/>", answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"Password\":\"foo\", \"Passphrase\" : \"foo bar\", \"SecretKey\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"Password\":\"xxxxx\", \"Passphrase\" : \"xxxxx\", \"SecretKey\" : \"xxxxx\"}",
                answer);
    }

    @Test
    public void testDisableJsonMask() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(true, true, false);
        String answer
                = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo　bar\", secretKey='!@#$%^&*() -+[]{};:'");
        assertEquals("key=value, myPassword=xxxxx,\n myPassphrase=\"xxxxx\", secretKey='xxxxx'", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        assertEquals("<xmlPassword>\n xxxxx \n</xmlPassword>\n<user password=\"xxxxx\"/>", answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}",
                answer);
    }

    @Test
    public void testCustomMaskString() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        formatter.setMaskString("**********");
        String answer
                = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo　bar\", secretKey='!@#$%^&*() -+[]{};:'");
        assertEquals("key=value, myPassword=**********,\n myPassphrase=\"**********\", secretKey='**********'", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        assertEquals("<xmlPassword>\n ********** \n</xmlPassword>\n<user password=\"**********\"/>", answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"Password\":\"foo\", \"Passphrase\" : \"foo bar\", \"SecretKey\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"Password\":\"**********\", \"Passphrase\" : \"**********\", \"SecretKey\" : \"**********\"}",
                answer);
    }

    @Test
    public void testDifferentSensitiveKeys() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        String answer
                = formatter.format("key=value, myAccessKey=foo,\n authkey=\"foo bar\", refreshtoken='!@#$%^&*() -+[]{};:'");
        assertEquals("key=value, myAccessKey=xxxxx,\n authkey=\"xxxxx\", refreshtoken='xxxxx'", answer);

        answer = formatter.format("<subscribeKey>\n foo bar \n</subscribeKey>\n<user verificationCode=\"asdf qwert\"/>");
        assertEquals("<subscribeKey>\n xxxxx \n</subscribeKey>\n<user verificationCode=\"xxxxx\"/>", answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"subscribeKey\":\"foo\", \"verificationCode\" : \"foo bar\", \"RefreshToken\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"subscribeKey\":\"xxxxx\", \"verificationCode\" : \"xxxxx\", \"RefreshToken\" : \"xxxxx\"}",
                answer);
    }

    @Test
    public void testCustomKeywords() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        formatter.addKeyword("cheese");
        formatter.setMaskString("**********");
        String answer
                = formatter.format(
                        "key=value, Cheese=gauda, myPassword=foo,\n myPassphrase=\"foo　bar\", secretKey='!@#$%^&*() -+[]{};:'");
        assertEquals(
                "key=value, Cheese=**********, myPassword=**********,\n myPassphrase=\"**********\", secretKey='**********'",
                answer);

        answer = formatter
                .format("<chEEse>Gauda</chEEse><xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        assertEquals("<chEEse>**********</chEEse><xmlPassword>\n ********** \n</xmlPassword>\n<user password=\"**********\"/>",
                answer);

        answer = formatter.format(
                "{\"key\" : \"value\", \"Cheese\": \"gauda\", \"Password\":\"foo\", \"Passphrase\" : \"foo bar\", \"SecretKey\" : \"!@#$%^&*() -+[]{};:'\"}");
        assertEquals(
                "{\"key\" : \"value\", \"Cheese\": \"**********\", \"Password\":\"**********\", \"Passphrase\" : \"**********\", \"SecretKey\" : \"**********\"}",
                answer);
    }

}
