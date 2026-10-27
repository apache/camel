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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Compat;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Source;
import org.apache.camel.tooling.maven.MavenArtifactDescriptor;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginCompatibilityTest {

    @Test
    void declaredRange() {
        Compat compat = PluginCompatibility.of(descriptor("4.23.0", "[4.23.0,4.24.0)"));
        assertThat(compat.source()).isEqualTo(Source.DECLARED);
        assertThat(compat.range()).isEqualTo("[4.23.0,4.24.0)");
        assertThat(compat.builtFor()).isEqualTo("4.23.0");
    }

    @Test
    void declaredRangeWinsOverDependency() {
        // a plugin built with 4.22 that also supports 4.23
        Compat compat = PluginCompatibility.of(descriptor("4.22.2", "[4.22.0,4.24.0)"));
        assertThat(compat.source()).isEqualTo(Source.DECLARED);
        assertThat(PluginCompatibility.accepts(compat.range(), "4.23.1")).isTrue();
    }

    @Test
    void inferredFromCamelJBangCore() {
        Compat compat = PluginCompatibility.of(descriptor("4.22.2", null));
        assertThat(compat.source()).isEqualTo(Source.INFERRED);
        assertThat(compat.range()).isEqualTo("[4.22.0,4.23.0)");
        assertThat(compat.builtFor()).isEqualTo("4.22.2");
        assertThat(compat.describe()).isEqualTo("built for Camel 4.22.2");
    }

    @Test
    void inferredFromSnapshotDependency() {
        Compat compat = PluginCompatibility.of(descriptor("4.23.0-SNAPSHOT", null));
        assertThat(compat.source()).isEqualTo(Source.INFERRED);
        assertThat(compat.range()).isEqualTo("[4.23.0,4.24.0)");
    }

    @Test
    void unknown() {
        Compat compat = PluginCompatibility.of(descriptor(null, null));
        assertThat(compat.source()).isEqualTo(Source.UNKNOWN);
        assertThat(compat.range()).isNull();
        assertThat(compat.builtFor()).isNull();
    }

    @Test
    void malformedRangeIsUnknown() {
        assertThat(PluginCompatibility.of(descriptor("4.22.2", "[4.23.0,")).source()).isEqualTo(Source.UNKNOWN);
        assertThat(PluginCompatibility.of(descriptor("4.22.2", "not a range")).source()).isEqualTo(Source.UNKNOWN);
        // a single version is not a range
        assertThat(PluginCompatibility.of(descriptor("4.22.2", "4.23.0")).source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void acceptsBoundaries() {
        String range = "[4.22.0,4.23.0)";
        assertThat(PluginCompatibility.accepts(range, "4.22.0")).isTrue();
        assertThat(PluginCompatibility.accepts(range, "4.22.9")).isTrue();
        assertThat(PluginCompatibility.accepts(range, "4.23.0")).isFalse();
        assertThat(PluginCompatibility.accepts(range, "4.21.5")).isFalse();
    }

    @Test
    void acceptsSnapshotCli() {
        assertThat(PluginCompatibility.accepts("[4.23.0,4.24.0)", "4.23.0-SNAPSHOT")).isTrue();
        assertThat(PluginCompatibility.accepts("[4.23.0,4.24.0)", "4.24.0-SNAPSHOT")).isFalse();
    }

    @Test
    void acceptsInvalid() {
        assertThat(PluginCompatibility.accepts(null, "4.23.0")).isFalse();
        assertThat(PluginCompatibility.accepts("[4.23.0,", "4.23.0")).isFalse();
        assertThat(PluginCompatibility.accepts("[4.23.0,4.24.0)", null)).isFalse();
    }

    @Test
    void minorLine() {
        assertThat(PluginCompatibility.minorLine("4.22.2")).isEqualTo("4.22");
        assertThat(PluginCompatibility.minorLine("4.23.0-SNAPSHOT")).isEqualTo("4.23");
        assertThat(PluginCompatibility.minorLine("foo")).isNull();
        assertThat(PluginCompatibility.minorLine(null)).isNull();
    }

    public static MavenArtifactDescriptor descriptor(String camelJBangCoreVersion, String camelVersions) {
        List<MavenGav> deps = new ArrayList<>();
        deps.add(dependency("org.example", "other", "1.0.0", "compile"));
        if (camelJBangCoreVersion != null) {
            deps.add(dependency("org.apache.camel", "camel-jbang-core", camelJBangCoreVersion, "provided"));
        }
        Map<String, String> props = new LinkedHashMap<>();
        if (camelVersions != null) {
            props.put(PluginCompatibility.CAMEL_VERSIONS_PROPERTY, camelVersions);
        }
        return new MavenArtifactDescriptor(
                MavenGav.fromCoordinates("org.example.plugins", "camel-jbang-plugin-fixture", "1.0.0", null, null), deps,
                props);
    }

    private static MavenGav dependency(String groupId, String artifactId, String version, String scope) {
        MavenGav gav = MavenGav.fromCoordinates(groupId, artifactId, version, null, null);
        gav.setScope(scope);
        return gav;
    }
}
