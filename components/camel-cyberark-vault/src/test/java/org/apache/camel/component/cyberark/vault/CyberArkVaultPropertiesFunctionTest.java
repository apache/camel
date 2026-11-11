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
package org.apache.camel.component.cyberark.vault;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CyberArkVaultPropertiesFunctionTest {

    private CyberArkVaultPropertiesFunction functionReturning(String secret) {
        ConjurClient client = mock(ConjurClient.class);
        when(client.retrieveSecret(any(), any())).thenReturn(secret);
        return new CyberArkVaultPropertiesFunction(client);
    }

    @Test
    void aField() {
        assertEquals("camel", functionReturning("{\"username\":\"camel\"}").apply("test#username"));
    }

    @Test
    void contentAfterTheFirstJsonValueIsIgnored() {
        assertEquals("camel", functionReturning("{\"username\":\"camel\"} trailing").apply("test#username"));
    }

    @Test
    void aFieldOfASecretThatIsNotJsonFailsWithoutADefault() {
        CyberArkVaultPropertiesFunction function = functionReturning("not-json");
        assertThrows(RuntimeCamelException.class, () -> function.apply("test#username"));
    }

    @Test
    void aFieldOfASecretThatIsNotJsonGivesTheDefault() {
        assertEquals("fallback", functionReturning("not-json").apply("test#username:fallback"));
    }

    @Test
    void aFieldThatIsNotAStringGivesTheDefault() {
        assertEquals("fallback", functionReturning("{\"port\":5432}").apply("test#port:fallback"));
    }
}
