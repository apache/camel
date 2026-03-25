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
package org.apache.camel.component.azure.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CredentialTypeTest {

    @Test
    void testAllCredentialTypesExist() {
        assertEquals(10, CredentialType.values().length);
    }

    @Test
    void testValueOfForAllTypes() {
        assertNotNull(CredentialType.valueOf("SHARED_KEY_CREDENTIAL"));
        assertNotNull(CredentialType.valueOf("SHARED_ACCOUNT_KEY"));
        assertNotNull(CredentialType.valueOf("AZURE_IDENTITY"));
        assertNotNull(CredentialType.valueOf("AZURE_SAS"));
        assertNotNull(CredentialType.valueOf("CONNECTION_STRING"));
        assertNotNull(CredentialType.valueOf("CLIENT_SECRET"));
        assertNotNull(CredentialType.valueOf("TOKEN_CREDENTIAL"));
        assertNotNull(CredentialType.valueOf("SERVICE_CLIENT_INSTANCE"));
        assertNotNull(CredentialType.valueOf("ACCESS_KEY"));
        assertNotNull(CredentialType.valueOf("FUNCTION_KEY"));
    }

    @Test
    void testValueOfMatchesComponentSpecificNames() {
        // These are the exact same enum value names used in component-specific enums,
        // ensuring backward compatibility when resolving from URI parameter strings
        assertEquals(CredentialType.AZURE_IDENTITY, CredentialType.valueOf("AZURE_IDENTITY"));
        assertEquals(CredentialType.CONNECTION_STRING, CredentialType.valueOf("CONNECTION_STRING"));
        assertEquals(CredentialType.SHARED_ACCOUNT_KEY, CredentialType.valueOf("SHARED_ACCOUNT_KEY"));
    }
}
