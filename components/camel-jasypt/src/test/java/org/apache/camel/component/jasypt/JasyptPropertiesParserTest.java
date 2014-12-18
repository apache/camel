/**
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
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class JasyptPropertiesParserTest {

    private static final String KEY = "somekey";

    private static final String ENCRYPTED_VALUE = "ENC(bsW9uV37gQ0QHFu7KO03Ww==)";
    private static final String DECRYPTED_VALUE = "tiger";

    private JasyptPropertiesParser jasyptPropertiesParser = new JasyptPropertiesParser();

    @Before
    public void before() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword("secret");
        jasyptPropertiesParser.setEncryptor(encryptor);
    }

    @Test
    public void testPlainPropertyIsUntouched() {
        String result = jasyptPropertiesParser.parseProperty(KEY, "abc?1=decrypted1&2=decrypted2&cde=()", null);
        assertThat(result, is("abc?1=decrypted1&2=decrypted2&cde=()"));
    }

    @Test
    public void testDecryptsEncryptedProperty() {
        String result = jasyptPropertiesParser.parseProperty(KEY, ENCRYPTED_VALUE, null);
        assertThat(result, is(DECRYPTED_VALUE));
    }

    @Test
    public void testDecryptsSinglePartEncryptedProperty() {
        String result = jasyptPropertiesParser.parseProperty(KEY, "abc?1=" + ENCRYPTED_VALUE + "&cde=()", null);
        assertThat(result, is("abc?1=" + DECRYPTED_VALUE + "&cde=()"));
    }

    @Test
    public void testDecryptsMultiPartEncryptedProperty() {
        String result = jasyptPropertiesParser.parseProperty(KEY, "abc?1=" + ENCRYPTED_VALUE + "&2=" + ENCRYPTED_VALUE + "&cde=()", null);
        assertThat(result, is("abc?1=" + DECRYPTED_VALUE + "&2=" + DECRYPTED_VALUE + "&cde=()"));
    }

    @Test
    public void testUsesProvidedPasswordIfEncryptorIsNotSet() throws Exception {
        jasyptPropertiesParser.setEncryptor(null);
        jasyptPropertiesParser.setPassword("secret");

        assertEquals("foo", jasyptPropertiesParser.parseProperty(KEY, "foo", null));
        assertEquals(DECRYPTED_VALUE, jasyptPropertiesParser.parseProperty(KEY, ENCRYPTED_VALUE, null));
    }

    @Test
    public void testUsesProvidedPasswordFromSystemPropertyIfEncryptorIsNotSet() throws Exception {
        System.setProperty("myfoo", "secret");

        jasyptPropertiesParser.setEncryptor(null);
        jasyptPropertiesParser.setPassword("sys:myfoo");

        assertEquals("foo", jasyptPropertiesParser.parseProperty(KEY, "foo", null));
        assertEquals(DECRYPTED_VALUE, jasyptPropertiesParser.parseProperty(KEY, ENCRYPTED_VALUE, null));

        System.clearProperty("myfoo");
    }
}