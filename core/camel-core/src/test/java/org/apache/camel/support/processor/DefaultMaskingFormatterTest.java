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

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMaskingFormatterTest {

    @Test
    void testDefaultOption() {
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
    void testDisableKeyValueMask() {
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
    void testDisableXmlElementMask() {
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
    void testDisableJsonMask() {
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
    void testCustomMaskString() {
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
    void testDifferentSensitiveKeys() {
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
    void testCustomKeywords() {
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

    @Test
    void formatMasksConnectionStringUserInfoCredentials() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();

        assertThat(formatter.format("mongodb://user:pass@host:27017/db"))
                .isEqualTo("mongodb://user:xxxxx@host:27017/db");
        assertThat(formatter.format("amqp://admin:secret@broker:5672/vhost"))
                .isEqualTo("amqp://admin:xxxxx@broker:5672/vhost");
        assertThat(formatter.format("redis://:s3cret@redis:6379/0"))
                .isEqualTo("redis://:xxxxx@redis:6379/0");
        assertThat(formatter.format("uri=redis://default:s3cret@redis:6379/0 password=visible"))
                .isEqualTo("uri=redis://default:xxxxx@redis:6379/0 password=xxxxx");
    }

    @Test
    void formatMasksUserInfoInsideJsonWithoutSensitiveKeyName() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        String answer = formatter.format("{\"url\":\"mongodb://user:secret@host/db\",\"name\":\"app\"}");
        assertThat(answer).isEqualTo("{\"url\":\"mongodb://user:xxxxx@host/db\",\"name\":\"app\"}");
    }

    @Test
    void formatStillMasksQueryPasswordKeyValue() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        // key=value masking stops at comma/quote; a lone ?password= form is still masked
        assertThat(formatter.format("http://host/path?password=topsecret"))
                .isEqualTo("http://host/path?password=xxxxx");
        assertThat(formatter.format("password=topsecret, user=alice"))
                .isEqualTo("password=xxxxx, user=xxxxx");
    }

    @Test
    void formatMasksPemPrivateKeyBlocks() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        String source = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA0Z3VS5JJcds3xfn
                -----END RSA PRIVATE KEY-----
                """;
        String answer = formatter.format(source);
        assertThat(answer)
                .contains("-----BEGIN RSA PRIVATE KEY-----")
                .contains("xxxxx")
                .contains("-----END RSA PRIVATE KEY-----")
                .doesNotContain("MIIEowIBAAKCAQEA0Z3VS5JJcds3xfn");
    }

    @Test
    void formatDoesNotMaskCertificatesOrPublicKeys() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        String certificate = """
                -----BEGIN CERTIFICATE-----
                MIIDXTCCAkWgAwIBAgIJAKHBj
                -----END CERTIFICATE-----
                """;
        assertThat(formatter.format(certificate)).isEqualTo(certificate);

        String publicKey = """
                -----BEGIN PUBLIC KEY-----
                MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBA
                -----END PUBLIC KEY-----
                """;
        assertThat(formatter.format(publicKey)).isEqualTo(publicKey);
    }

    @Test
    void formatMasksValueShapesEvenWhenKeyValueMaskDisabled() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(false, false, false);
        assertThat(formatter.format("mongodb://user:pass@host/db"))
                .isEqualTo("mongodb://user:xxxxx@host/db");
        assertThat(formatter.format("""
                -----BEGIN PRIVATE KEY-----
                secret-bytes
                -----END PRIVATE KEY-----
                """)).doesNotContain("secret-bytes").contains("xxxxx");
    }

    @Test
    void formatMasksValueShapesWithCustomMaskString() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        formatter.setMaskString("***");
        assertThat(formatter.format("amqp://admin:secret@broker/vhost"))
                .isEqualTo("amqp://admin:***@broker/vhost");
    }

    @Test
    void formatNullAndEmptyUnchanged() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        assertThat(formatter.format(null)).isNull();
        assertThat(formatter.format("")).isEmpty();
    }

    @Test
    void formatPlainTextUnchanged() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        assertThat(formatter.format("Hello World")).isEqualTo("Hello World");
    }

    @Test
    void formatMasksValueShapesWhenKeywordsEmpty() {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(Collections.emptySet(), true, true, true);
        assertThat(formatter.format("mongodb://user:pass@host/db"))
                .isEqualTo("mongodb://user:xxxxx@host/db");
        assertThat(formatter.format("password=visible")).isEqualTo("password=visible");
    }

}
