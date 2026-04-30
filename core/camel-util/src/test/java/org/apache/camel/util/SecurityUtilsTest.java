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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityUtilsTest {

    @Test
    void testGetSecurityOptions() {
        Map<String, SecurityUtils.SecurityOption> options = SecurityUtils.getSecurityOptions();
        assertNotNull(options);
        assertFalse(options.isEmpty());
        // verify known entries
        assertNotNull(options.get("trustallcertificates"));
        assertEquals("insecure:ssl", options.get("trustallcertificates").category());
    }

    @Test
    void testIsPlainTextSecret() {
        // plain text values should be detected
        assertTrue(SecurityUtils.isPlainTextSecret("mypassword123"));
        assertTrue(SecurityUtils.isPlainTextSecret("some-api-key"));

        // null and empty should not be considered plain text
        assertFalse(SecurityUtils.isPlainTextSecret(null));
        assertFalse(SecurityUtils.isPlainTextSecret(""));

        // RAW() is a URI encoding wrapper, not a security mechanism
        assertTrue(SecurityUtils.isPlainTextSecret("RAW(mypassword123)"));

        // vault references are secured
        assertFalse(SecurityUtils.isPlainTextSecret("{{vault:aws:mySecret}}"));

        // environment variable placeholders are secured
        assertFalse(SecurityUtils.isPlainTextSecret("${env:MY_PASSWORD}"));
        assertFalse(SecurityUtils.isPlainTextSecret("${ENV:MY_PASSWORD}"));

        // system property placeholders are secured
        assertFalse(SecurityUtils.isPlainTextSecret("${sys:my.password}"));
        assertFalse(SecurityUtils.isPlainTextSecret("${SYS:my.password}"));

        // property placeholders are secured
        assertFalse(SecurityUtils.isPlainTextSecret("{{my.password}}"));
    }

    @Test
    void testGetSecurityOption() {
        // full property key — should extract last segment
        SecurityUtils.SecurityOption opt = SecurityUtils.getSecurityOption("camel.component.http.trustAllCertificates");
        assertNotNull(opt);
        assertEquals("insecure:ssl", opt.category());
        assertEquals("true", opt.insecureValue());

        // kebab-case should be normalized
        opt = SecurityUtils.getSecurityOption("camel.ssl.trust-all-certificates");
        assertNotNull(opt);
        assertEquals("insecure:ssl", opt.category());

        // plain option name
        opt = SecurityUtils.getSecurityOption("allowJavaSerializedObject");
        assertNotNull(opt);
        assertEquals("insecure:serialization", opt.category());

        // unknown option
        assertNull(SecurityUtils.getSecurityOption("camel.component.http.someOtherOption"));
    }

    @Test
    void testIsInsecureValue() {
        // trustAllCertificates=true is insecure
        assertTrue(SecurityUtils.isInsecureValue("camel.ssl.trustAllCertificates", "true"));
        assertTrue(SecurityUtils.isInsecureValue("camel.ssl.trustAllCertificates", "TRUE"));

        // trustAllCertificates=false is safe
        assertFalse(SecurityUtils.isInsecureValue("camel.ssl.trustAllCertificates", "false"));

        // hostnameVerification=false is insecure (inverted semantics)
        assertTrue(SecurityUtils.isInsecureValue("camel.ssl.hostnameVerification", "false"));
        assertFalse(SecurityUtils.isInsecureValue("camel.ssl.hostnameVerification", "true"));

        // unknown option is never insecure
        assertFalse(SecurityUtils.isInsecureValue("camel.component.http.port", "8080"));
    }

    @Test
    void testDetectViolationsSecrets() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.component.http.password", "plaintext");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> k.contains("password"),
                category -> "warn",
                Set.of());

        assertEquals(1, violations.size());
        assertEquals("secret", violations.get(0).category());
        assertEquals("warn", violations.get(0).policy());
        assertTrue(violations.get(0).propertyKey().contains("password"));
    }

    @Test
    void testDetectViolationsInsecureOptions() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "fail",
                Set.of());

        assertEquals(1, violations.size());
        assertEquals("insecure:ssl", violations.get(0).category());
        assertEquals("fail", violations.get(0).policy());
    }

    @Test
    void testDetectViolationsSkipsSecurityConfigProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.security.policy", "fail");
        properties.put("camel.security.allowedProperties", "foo");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> true,
                category -> "fail",
                Set.of());

        assertEquals(0, violations.size());
    }

    @Test
    void testDetectViolationsAllowedKeys() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "fail",
                Set.of("camel.ssl.trustAllCertificates"));

        assertEquals(0, violations.size());
    }

    @Test
    void testDetectViolationsWithNullAllowedKeys() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "warn",
                null);

        assertEquals(1, violations.size());
    }

    @Test
    void testDetectViolationsAllowPolicy() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");
        properties.put("camel.component.http.password", "plaintext");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> k.contains("password"),
                category -> "allow",
                Set.of());

        assertEquals(0, violations.size());
    }

    @Test
    void testDetectViolationsSafeValues() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "false");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "fail",
                Set.of());

        assertEquals(0, violations.size());
    }
}
