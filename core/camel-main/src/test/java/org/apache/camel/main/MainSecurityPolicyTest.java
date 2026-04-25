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
package org.apache.camel.main;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.SecurityUtils;
import org.apache.camel.util.SecurityViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainSecurityPolicyTest {

    @Test
    public void testPlainTextSecretWarnByDefault() {
        // default policy is "warn" - should start without exception
        Main main = new Main();
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testPlainTextSecretFailPolicy() {
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "fail");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("Security policy violations detected"));
        assertTrue(ex.getMessage().contains("keystorePassword"));
    }

    @Test
    public void testPlainTextSecretAllowPolicy() {
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "allow");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testPlainTextSecretAllowedProperty() {
        // even with fail policy, allowed properties should pass
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "fail");
        main.addInitialProperty("camel.security.allowedProperties", "camel.ssl.keystorePassword");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testPlainTextSecretWithRAW() {
        // RAW() is a URI encoding wrapper, not a security mechanism — RAW(password) is still plain text
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "fail");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "RAW(my-secure-password)");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        // should throw because RAW() does not make the value secure
        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("keystorePassword"));
    }

    @Test
    public void testPlainTextSecretWithEnvPlaceholder() {
        // env var placeholder values should not be flagged
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "fail");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "${env:KEYSTORE_PASSWORD}");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testGlobalPolicyAppliesToSecrets() {
        // global policy=fail should apply to secrets when no specific secretPolicy
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("Security policy violations detected"));
    }

    @Test
    public void testCategoryPolicyOverridesGlobal() {
        // global=fail but secretPolicy=allow should allow plain-text secrets
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.security.secretPolicy", "allow");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testSecurityConfigurationProperties() {
        SecurityConfigurationProperties props = new SecurityConfigurationProperties(null);

        // default policy
        props.setPolicy("warn");
        assertEquals("warn", props.getPolicy());
        assertEquals("warn", props.resolvePolicy("secret"));
        assertEquals("warn", props.resolvePolicy("insecure:ssl"));

        // category override
        props.setSecretPolicy("fail");
        assertEquals("fail", props.getSecretPolicy());
        assertEquals("fail", props.resolvePolicy("secret"));
        assertEquals("warn", props.resolvePolicy("insecure:ssl"));

        // ssl category override
        props.setInsecureSslPolicy("allow");
        assertEquals("allow", props.resolvePolicy("insecure:ssl"));

        // serialization category override
        props.setInsecureSerializationPolicy("fail");
        assertEquals("fail", props.resolvePolicy("insecure:serialization"));

        // dev category override
        props.setInsecureDevPolicy("allow");
        assertEquals("allow", props.resolvePolicy("insecure:dev"));

        // unknown category falls back to global
        assertEquals("warn", props.resolvePolicy("unknown"));

        // allowed properties getter
        props.setAllowedProperties("foo,bar");
        assertEquals("foo,bar", props.getAllowedProperties());
    }

    @Test
    public void testAllowedProperties() {
        SecurityConfigurationProperties props = new SecurityConfigurationProperties(null);

        assertFalse(props.isAllowed("camel.ssl.keystorePassword"));

        props.setAllowedProperties("camel.ssl.keystorePassword,camel.component.http.trustAllCertificates");
        assertTrue(props.isAllowed("camel.ssl.keystorePassword"));
        assertTrue(props.isAllowed("camel.component.http.trustAllCertificates"));
        assertFalse(props.isAllowed("camel.ssl.trustStorePassword"));
    }

    @Test
    public void testInsecureSslFailPolicy() {
        // insecure:ssl policy=fail should block trustAllCertificates=true
        Main main = new Main();
        main.addInitialProperty("camel.security.insecureSslPolicy", "fail");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("Security policy violations detected"));
        assertTrue(ex.getMessage().contains("trustAllCertificates"));
    }

    @Test
    public void testInsecureSslWarnByDefault() {
        // default policy is "warn" - should start without exception
        Main main = new Main();
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @ParameterizedTest(name = "insecure SSL allowed with: {0}")
    @MethodSource("insecureSslAllowedScenarios")
    public void testInsecureSslAllowed(String scenario, Map<String, String> properties) {
        Main main = new Main();
        properties.forEach(main::addInitialProperty);
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    static Stream<Arguments> insecureSslAllowedScenarios() {
        return Stream.of(
                Arguments.of("explicit allow policy",
                        Map.of("camel.security.insecureSslPolicy", "allow")),
                Arguments.of("dev profile (default warn)",
                        Map.of("camel.main.profile", "dev")),
                Arguments.of("test profile (default warn)",
                        Map.of("camel.main.profile", "test")));
    }

    @Test
    public void testInsecureSslCategoryOverridesGlobal() {
        // global=fail but insecureSslPolicy=allow should allow insecure SSL
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.security.insecureSslPolicy", "allow");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testInsecureSerializationViaDetectViolations() {
        // test insecure:serialization detection via SecurityUtils.detectViolations()
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.component.jms.allowJavaSerializedObject", "true");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "insecure:serialization".equals(category) ? "fail" : "warn",
                Set.of());

        assertEquals(1, violations.size());
        assertEquals("insecure:serialization", violations.get(0).category());
        assertTrue(violations.get(0).propertyKey().contains("allowJavaSerializedObject"));
    }

    @Test
    public void testInsecureDevViaDetectViolations() {
        // test insecure:dev detection via SecurityUtils.detectViolations()
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.main.devConsoleEnabled", "true");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "insecure:dev".equals(category) ? "fail" : "warn",
                Set.of());

        assertEquals(1, violations.size());
        assertEquals("insecure:dev", violations.get(0).category());
        assertTrue(violations.get(0).propertyKey().contains("devConsoleEnabled"));
    }

    @Test
    public void testInsecureValueNotTriggeredForSafeValues() {
        // trustAllCertificates=false should NOT trigger a violation
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "false");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "fail",
                Set.of());

        assertEquals(0, violations.size());
    }

    @Test
    public void testInsecureSslAllowedProperty() {
        // fail policy but trustAllCertificates is in the allowed list
        Main main = new Main();
        main.addInitialProperty("camel.security.insecureSslPolicy", "fail");
        main.addInitialProperty("camel.security.allowedProperties", "camel.ssl.trustAllCertificates");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testInvalidPolicyValueThrows() {
        SecurityConfigurationProperties props = new SecurityConfigurationProperties(null);

        assertThrows(IllegalArgumentException.class, () -> props.setPolicy("block"));
        assertThrows(IllegalArgumentException.class, () -> props.setSecretPolicy("invalid"));

        // valid values should not throw (case-insensitive)
        assertDoesNotThrow(() -> props.setPolicy("allow"));
        assertDoesNotThrow(() -> props.setPolicy("warn"));
        assertDoesNotThrow(() -> props.setPolicy("fail"));
        assertDoesNotThrow(() -> props.setInsecureSslPolicy("FAIL"));
        assertDoesNotThrow(() -> props.setPolicy("Warn"));
        assertDoesNotThrow(() -> props.setSecretPolicy(null));
    }

    @Test
    public void testInvalidPolicyValueOnMainThrows() {
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "invalid-value");

        assertThrows(Exception.class, main::start);
    }

    @Test
    public void testSecurityPolicyResultStoredAsContextPlugin() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        main.start();
        try {
            SecurityPolicyResult result
                    = main.getCamelContext().getCamelContextExtension().getContextPlugin(SecurityPolicyResult.class);
            assertNotNull(result);
            assertTrue(result.hasViolations());
            assertEquals(1, result.getViolationCount());
            assertEquals("insecure:ssl", result.getViolations().get(0).category());
            assertEquals("warn", result.getViolations().get(0).policy());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testSecurityPolicyResultCleanWhenNoViolations() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.security.insecureSslPolicy", "allow");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        main.start();
        try {
            SecurityPolicyResult result
                    = main.getCamelContext().getCamelContextExtension().getContextPlugin(SecurityPolicyResult.class);
            assertNotNull(result);
            // allow policy means violations are not detected (filtered out)
            assertFalse(result.hasViolations());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testDetectViolationsUtility() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");
        properties.put("camel.component.http.password", "plaintext");
        properties.put("camel.security.policy", "warn");
        properties.put("camel.main.name", "test");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> k.contains("password"),
                category -> "warn",
                Set.of());

        // should find trustAllCertificates (insecure:ssl) and password (secret)
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "insecure:ssl".equals(v.category())));
        assertTrue(violations.stream().anyMatch(v -> "secret".equals(v.category())));
    }

    @Test
    public void testDetectViolationsRespectsAllowedKeys() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> false,
                category -> "warn",
                Set.of("camel.ssl.trustAllCertificates"));

        // allowed key should be excluded
        assertEquals(0, violations.size());
    }

    @Test
    public void testProdProfileDefaultsToFail() {
        // production profile should default security policy to "fail"
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("Security policy violations detected"));
    }

    @Test
    public void testProdProfilePolicyCanBeOverridden() {
        // user can override the prod profile default by explicitly setting policy=warn
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.security.policy", "warn");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testDevProfileAllowsInsecureDevOptions() {
        // dev profile should auto-allow insecure:dev options
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.main.devConsoleEnabled", "true");

        // should not throw because dev profile defaults insecureDevPolicy=allow
        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    public void testMultipleViolationsInFailMode() {
        // multiple violations should all be reported in the error message
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("trustAllCertificates"));
        assertTrue(ex.getMessage().contains("keystorePassword"));
    }

    @Test
    public void testCaseInsensitivePolicyValues() {
        // policy values should be case-insensitive
        SecurityConfigurationProperties props = new SecurityConfigurationProperties(null);
        props.setPolicy("WARN");
        assertEquals("warn", props.getPolicy());
        props.setPolicy("Fail");
        assertEquals("fail", props.getPolicy());
        props.setInsecureSslPolicy("ALLOW");
        assertEquals("allow", props.getInsecureSslPolicy());
    }

    @Test
    public void testViolationCarriesPolicy() {
        // violations should carry the effective policy from detection
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("camel.ssl.trustAllCertificates", "true");
        properties.put("camel.component.http.password", "plaintext");

        List<SecurityViolation> violations = SecurityUtils.detectViolations(
                properties,
                (k, v) -> k.contains("password"),
                category -> "secret".equals(category) ? "fail" : "warn",
                Set.of());

        assertEquals(2, violations.size());
        SecurityViolation sslViolation = violations.stream()
                .filter(v -> "insecure:ssl".equals(v.category())).findFirst().orElseThrow();
        assertEquals("warn", sslViolation.policy());

        SecurityViolation secretViolation = violations.stream()
                .filter(v -> "secret".equals(v.category())).findFirst().orElseThrow();
        assertEquals("fail", secretViolation.policy());
    }
}
