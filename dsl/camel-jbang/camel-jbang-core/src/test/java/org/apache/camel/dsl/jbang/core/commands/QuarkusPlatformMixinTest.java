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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.Properties;

import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_EXTENSION_REGISTRY_BASE_URI;
import static org.assertj.core.api.Assertions.assertThat;

class QuarkusPlatformMixinTest {

    private static final QuarkusPlatformMixinSpec DEFAULT_FALLBACK = new QuarkusPlatformMixinSpec() {
        @Override
        public String quarkusGroupId() {
            return "io.quarkus.platform";
        }

        @Override
        public String quarkusVersion() {
            return null;
        }

        @Override
        public String quarkusExtensionRegistryBaseUri() {
            return RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL;
        }
    };

    @Test
    void platformUrlFromPropertiesIsHonoured() {
        Properties props = new Properties();
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://my.registry.example.com");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://my.registry.example.com");
    }

    @Test
    void platformUrlSuffixStrippedWhenReadFromProperties() {
        Properties props = new Properties();
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY,
                "https://my.registry.example.com/client/platforms");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://my.registry.example.com");
    }

    @Test
    void platformUrlTrailingSlashIsStripped() {
        Properties props = new Properties();
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://my.registry.example.com/");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://my.registry.example.com");
    }

    @Test
    void canonicalKeyTakesPriorityOverLegacyKey() {
        Properties props = new Properties();
        props.setProperty(QUARKUS_EXTENSION_REGISTRY_BASE_URI, "https://canonical.example.com");
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://legacy.example.com");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://canonical.example.com");
    }

    @Test
    void fallbackUsedWhenNeitherPropertyIsSet() {
        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(new Properties(), DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri())
                .isEqualTo(RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL);
    }

    @Test
    void twoPhaseLoadingPreservesFirstValue() {
        Properties appProps = new Properties();
        appProps.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://from-app-props.example.com");
        QuarkusPlatformMixin afterPhase1 = QuarkusPlatformMixin.of(appProps, DEFAULT_FALLBACK);

        QuarkusPlatformMixin afterPhase2 = QuarkusPlatformMixin.of(new Properties(), afterPhase1);

        assertThat(afterPhase2.quarkusExtensionRegistryBaseUri()).isEqualTo("https://from-app-props.example.com");
    }

    @Test
    void twoPhaseLoadingAllowsOverride() {
        Properties appProps = new Properties();
        appProps.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://from-app-props.example.com");
        QuarkusPlatformMixin afterPhase1 = QuarkusPlatformMixin.of(appProps, DEFAULT_FALLBACK);

        Properties sysProps = new Properties();
        sysProps.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://from-sys-prop.example.com");
        QuarkusPlatformMixin afterPhase2 = QuarkusPlatformMixin.of(sysProps, afterPhase1);

        assertThat(afterPhase2.quarkusExtensionRegistryBaseUri()).isEqualTo("https://from-sys-prop.example.com");
    }

}
