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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Optional;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationValidateToolsTest {

    private ConfigurationValidateTools createTools() {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.empty();

        ConfigurationValidateTools tools = new ConfigurationValidateTools();
        tools.catalogService = catalogService;
        return tools;
    }

    @Test
    void validatesValidMainProperty() {
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.main.streamCachingEnabled=true", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
        assertThat(result.summary().total()).isEqualTo(1);
        assertThat(result.summary().valid()).isEqualTo(1);
        assertThat(result.summary().errors()).isZero();
        assertThat(result.properties()).hasSize(1);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.lineNumber()).isEqualTo(1);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isTrue();
        assertThat(info.errors()).isZero();
        assertThat(info.issues()).isEmpty();
    }

    @Test
    void theRunningVersionWithoutSnapshotIsTheDefaultCatalog() {
        // an assistant that reads "4.24.0-SNAPSHOT" from the server asks for 4.23.0, which is not on Maven Central
        ConfigurationValidateTools tools = createTools();
        String mine = tools.catalogService.getDefaultCatalog().getCatalogVersion().replace("-SNAPSHOT", "");

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.main.streamCachingEnabled=true", "main", mine, null);

        assertThat(result.summary().valid()).isEqualTo(1);
    }

    @Test
    void detectsUnknownOption() {
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.main.streamCachngEnabled=true", null, null, null);

        assertThat(result.summary().errors()).isPositive();
        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.valid()).isFalse();
        assertThat(info.issues()).anyMatch(i -> "unknown".equals(i.kind()));
    }

    @Test
    void detectsInvalidBoolean() {
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.main.streamCachingEnabled=notabool", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.valid()).isFalse();
        assertThat(info.issues()).anyMatch(i -> "invalidBoolean".equals(i.kind())
                && i.message().contains("notabool"));
    }

    @Test
    void acceptsPlaceholderValuesAcrossTypes() {
        ConfigurationValidateTools tools = createTools();

        // Property placeholders ({{...}}) should be accepted as a value for any option type
        // (boolean, integer, string, ...) since the actual value is resolved at runtime.
        String input = "camel.main.streamCachingEnabled={{myCacheEnabled}}\n"
                       + "camel.main.durationMaxSeconds={{myDurationSeconds}}\n"
                       + "camel.main.uuidGenerator={{myUuidGenerator}}";

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate(input, null, null, null);

        assertThat(result.summary().total()).isEqualTo(3);
        assertThat(result.summary().valid()).isEqualTo(3);
        assertThat(result.summary().errors()).isZero();
        assertThat(result.properties())
                .allSatisfy(info -> {
                    assertThat(info.checked()).isTrue();
                    assertThat(info.valid()).isTrue();
                    assertThat(info.errors()).isZero();
                    assertThat(info.issues()).isEmpty();
                });
    }

    @Test
    void acceptsSpringStylePlaceholderValue() {
        ConfigurationValidateTools tools = createTools();

        // Spring/Quarkus style ${...} placeholders must also be accepted for typed options.
        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.main.streamCachingEnabled=${my.cache.enabled}", null, null,
                        null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isTrue();
        assertThat(info.errors()).isZero();
        assertThat(info.issues()).isEmpty();
    }

    @Test
    void skipsBlankLinesAndComments() {
        ConfigurationValidateTools tools = createTools();

        String input = """
                # this is a comment
                ! shell-style comment

                camel.main.streamCachingEnabled=true
                """;

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate(input, null, null, null);

        assertThat(result.summary().total()).isEqualTo(1);
        assertThat(result.properties()).hasSize(1);
        assertThat(result.properties().get(0).lineNumber()).isEqualTo(4);
    }

    @Test
    void multipleLinesAggregated() {
        ConfigurationValidateTools tools = createTools();

        String input = "camel.main.streamCachingEnabled=true\n"
                       + "camel.main.streamCachngEnabled=true\n"
                       + "camel.main.streamCachingEnabled=notabool";

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate(input, null, null, null);

        assertThat(result.summary().total()).isEqualTo(3);
        assertThat(result.summary().valid()).isEqualTo(1);
        assertThat(result.summary().errors()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void nonCamelKeyMarkedNotChecked() {
        // Non-Camel keys (e.g. server.port) are not checked by this catalog: checked=false,
        // valid=false, and counted in summary.unchecked (not in summary.errors or summary.valid).
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("server.port=8080", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isFalse();
        assertThat(info.valid()).isFalse();
        assertThat(result.summary().unchecked()).isEqualTo(1);
        assertThat(result.summary().errors()).isZero();
        assertThat(result.summary().valid()).isZero();
    }

    // --- CAMEL-25538 regression tests ---

    @Test
    void camelServerPortIsAcceptedAndValid() {
        // camel.server.* was previously not in the hardcoded prefix list — it must now be
        // accepted and validated against the main-model metadata.
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.server.port=9999", null, null, null);

        assertThat(result.summary().total()).isEqualTo(1);
        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isTrue();
        assertThat(info.errors()).isZero();
        assertThat(info.issues()).isEmpty();
    }

    @Test
    void camelServerInventedKeyIsCheckedButNotValid() {
        // An invented key under an accepted camel.server.* group must be reported as unknown,
        // not silently passed as valid.
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.server.bindPort=9999", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isFalse();
        assertThat(info.issues()).anyMatch(i -> "unknown".equals(i.kind()));
    }

    @Test
    void uncheckedKeyImpliesValidFalseAndNotCountedInErrors() {
        // Invented key outside any known camel.* group: checked=false AND valid=false.
        // Before the fix, valid was true despite accepted=false (CAMEL-25538 bug #2).
        // The key must not appear in summary.errors — it is unchecked, not rejected.
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.invented.completely.unknown=foo", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isFalse();
        assertThat(info.valid()).isFalse();
        // The summary must not count unchecked keys as valid or as errors
        assertThat(result.summary().valid()).isZero();
        assertThat(result.summary().errors()).isZero();
        assertThat(result.summary().unchecked()).isEqualTo(1);
    }

    @Test
    void camelManagementKeyIsCheckedAndValid() {
        // camel.management.* was also missing from the old hardcoded list.
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.management.enabled=true", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isTrue();
    }

    @Test
    void camelVaultAwsKeyIsCheckedAndValid() {
        // camel.vault.aws.* is a nested group (3-segment name) — must also be accepted.
        // The dot-offset logic that skips the group prefix is exercised here.
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.vault.aws.accessKey=AKIA1234", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isTrue();
        assertThat(info.issues()).isEmpty();
    }

    @Test
    void camelVaultAwsInventedKeyIsCheckedButNotValid() {
        // An invented option under a nested group (camel.vault.aws.*) exercises the dot-offset
        // logic: the option name starts after the 3rd dot and must be checked against the model.
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.vault.aws.inventedOption=foo", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isFalse();
    }

    @Test
    void dashedGroupNameIsCheckedAndValid() {
        // camel-main binds camel.error-registry.* to the camel.errorRegistry group
        ConfigurationValidateTools tools = createTools();

        ConfigurationValidateTools.ConfigurationValidateResult result
                = tools.camel_configuration_validate("camel.error-registry.enabled=true", null, null, null);

        ConfigurationValidateTools.PropertyValidationInfo info = result.properties().get(0);
        assertThat(info.checked()).isTrue();
        assertThat(info.valid()).isTrue();
    }

    @Test
    void blankInputThrows() {
        ConfigurationValidateTools tools = createTools();

        assertThatThrownBy(() -> tools.camel_configuration_validate("   ", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    @Test
    void nullInputThrows() {
        ConfigurationValidateTools tools = createTools();

        assertThatThrownBy(() -> tools.camel_configuration_validate(null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }
}
