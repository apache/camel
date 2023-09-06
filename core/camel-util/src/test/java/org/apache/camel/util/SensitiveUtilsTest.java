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

package org.apache.camel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveUtilsTest {

    @Test
    void testContainsSensitive() {
        assertTrue(SensitiveUtils.containsSensitive("accessKey"));
        assertTrue(SensitiveUtils.containsSensitive("accesstoken"));
        assertTrue(SensitiveUtils.containsSensitive("authorizationtoken"));
        assertTrue(SensitiveUtils.containsSensitive("clientsecret"));
        assertTrue(SensitiveUtils.containsSensitive("passphrase"));
        assertTrue(SensitiveUtils.containsSensitive("password"));
        assertTrue(SensitiveUtils.containsSensitive("sasljaasconfig"));
        assertTrue(SensitiveUtils.containsSensitive("sasl-jaas-config"));
        assertTrue(SensitiveUtils.containsSensitive("saslJaasConfig"));
        assertTrue(SensitiveUtils.containsSensitive("secret"));
        assertTrue(SensitiveUtils.containsSensitive("secretkey"));
        assertTrue(SensitiveUtils.containsSensitive("secret-key"));
        assertTrue(SensitiveUtils.containsSensitive("secretKey"));
        assertTrue(SensitiveUtils.containsSensitive("secret-Key"));
        assertTrue(SensitiveUtils.containsSensitive("access-key"));
        assertTrue(SensitiveUtils.containsSensitive("accessKey"));
        assertTrue(SensitiveUtils.containsSensitive("access-Key"));
        assertTrue(SensitiveUtils.containsSensitive("client-secret"));
        assertTrue(SensitiveUtils.containsSensitive("authorization-token"));
        assertTrue(SensitiveUtils.containsSensitive("foo.bar.accessKey"));

        assertFalse(SensitiveUtils.containsSensitive("foo.bar.accessKey."));
        assertFalse(SensitiveUtils.containsSensitive("foo"));
        assertFalse(SensitiveUtils.containsSensitive("bar"));
    }

}
