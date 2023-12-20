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
package org.apache.camel.component.jasypt;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.apache.camel.component.jasypt.JasyptPropertiesParser.JASYPT_PREFIX_TOKEN;
import static org.apache.camel.component.jasypt.JasyptPropertiesParser.JASYPT_SUFFIX_TOKEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JasyptPropertiesParserTest {

    private static final String KEY = "somekey";

    protected String knownPassword = "secret";
    protected String knownEncrypted = "ENC(bsW9uV37gQ0QHFu7KO03Ww==)";
    protected String knowDecrypted = "tiger";

    protected JasyptPropertiesParser jasyptPropertiesParser = new JasyptPropertiesParser();
    protected StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

    @BeforeEach
    public void before() {
        encryptor.setPassword(knownPassword);

        jasyptPropertiesParser.setEncryptor(encryptor);
    }

    @Test
    public void testNullPropertyIsUntouched() {
        String expected = null;
        String result = jasyptPropertiesParser.parseProperty(KEY, expected, null);
        assertThat(result, is(expected));
    }

    @Test
    public void testPlainPropertyIsUntouched() {
        String expected = "http://somehost?1=someval1&2=someval2";
        String result = jasyptPropertiesParser.parseProperty(KEY, expected, null);
        assertThat(result, is(expected));
    }

    @Test
    public void testDecryptsEncryptedProperty() {
        String decrypted = "tiger";
        String encrypted = format("%s%s%s", JASYPT_PREFIX_TOKEN, encryptor.encrypt(decrypted), JASYPT_SUFFIX_TOKEN);
        String result = jasyptPropertiesParser.parseProperty(KEY, encrypted, null);
        assertThat(result, is(decrypted));
    }

    @Test
    public void testDecryptsPartiallyEncryptedProperty() {
        String parmValue = "tiger";
        String encParmValue = format("%s%s%s", JASYPT_PREFIX_TOKEN, encryptor.encrypt(parmValue), JASYPT_SUFFIX_TOKEN);

        String expected = format("http://somehost:port/?param1=%s&param2=somethingelse", parmValue);
        String propertyValue = format("http://somehost:port/?param1=%s&param2=somethingelse", encParmValue);

        String result = jasyptPropertiesParser.parseProperty(KEY, propertyValue, null);
        assertThat(result, is(expected));
    }

    @Test
    public void testDecryptsMultitplePartsOfPartiallyEncryptedProperty() {
        StringBuilder propertyValue = new StringBuilder();
        StringBuilder expected = new StringBuilder();

        for (int i = 0; i < 100; i++) {
            propertyValue.append(format("param%s=%s%s%s()&", i,
                    JASYPT_PREFIX_TOKEN, encryptor.encrypt("tiger" + i), JASYPT_SUFFIX_TOKEN));
            expected.append(format("param%s=tiger%s()&", i, i));
        }
        String result = jasyptPropertiesParser.parseProperty(KEY, propertyValue.toString(), null);
        assertThat(result, is(expected.toString()));
    }

    @Test
    public void testUsesProvidedPasswordIfEncryptorIsNotSet() {
        jasyptPropertiesParser.setEncryptor(null);
        jasyptPropertiesParser.setPassword(knownPassword);

        assertEquals(knowDecrypted, jasyptPropertiesParser.parseProperty(KEY, knownEncrypted, null));
    }

    @Test
    public void testUsesProvidedPasswordFromSystemPropertyIfEncryptorIsNotSet() {
        System.setProperty("myfoo", knownPassword);

        jasyptPropertiesParser.setEncryptor(null);
        jasyptPropertiesParser.setPassword("sys:myfoo");

        assertEquals(knowDecrypted, jasyptPropertiesParser.parseProperty(KEY, knownEncrypted, null));

        System.clearProperty("myfoo");
    }
}
