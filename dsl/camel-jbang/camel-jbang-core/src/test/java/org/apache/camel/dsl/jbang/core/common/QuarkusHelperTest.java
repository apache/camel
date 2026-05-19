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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.CamelVersionInPlatformRelease;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.MajorMinor;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class QuarkusHelperTest {
    @Test
    void findPlatformVersion() throws IOException, DeserializationException {

        // a local copy of https://registry.quarkus.io/client/platforms
        String src = Files.readString(Path.of("target/test-classes/QuarkusHelperTest/quarkus-registry-client-platforms.json"));
        JsonObject json = (JsonObject) Jsoner.deserialize(src);
        JsonArray platforms = json.getCollection("platforms");
        JsonObject platform = platforms.getMap(0);
        JsonArray streams = platform.getCollection("streams");

        // BOM 3.27.3.1 has Camel 4.14.5 and there is no other one with Camel 4.14.x so a BOM with newer Camel is returned
        assertPlatformVersion(streams, "4.14.0", "3.27.3.1");
        // perfect match
        assertPlatformVersion(streams, "4.14.5", "3.27.3.1");
        // BOM 3.27.3.1 has Camel 4.14.5 and there is no other one with Camel 4.14.x so a BOM with older Camel is returned
        assertPlatformVersion(streams, "4.14.10", "3.27.3.1");

        assertPlatformVersion(streams, "4.18.0", "3.33.1.1");

        assertPlatformVersion(streams, "4.20.0", "3.35.2");
        // There is no BOM with Camel 5.x so a BOM with latest Camel is returned
        assertPlatformVersion(streams, "5.0.0", "3.35.2");

        assertPlatformVersion(streams, "3.16.0", "2.8.0.Final");
        // There is no BOM with Camel 3.17 so a BOM with an older Camel 3.x is returned
        assertPlatformVersion(streams, "3.17.0", "2.8.0.Final");
    }

    private void assertPlatformVersion(JsonArray streams, String camelVersion, String expectedPlatformVersion) {
        Optional<String> resolved = QuarkusHelper.findPlatformBom(
                streams, new MajorMinor(camelVersion),
                QuarkusHelperTest::resolve,
                RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL)
                .map(QuarkusPlatformBom::quarkusCamelBom)
                .map(MavenGav::getVersion);
        Assertions.assertThat(resolved).isPresent().contains(expectedPlatformVersion);
    }

    static MavenArtifact resolve(MavenGav resolverGatv) {
        MavenArtifact result = new MavenArtifact(
                resolverGatv,
                Path.of("target/test-classes/QuarkusHelperTest/quarkus-camel-bom-" + resolverGatv.getVersion() + ".pom.xml")
                        .toFile());
        return result;
    }

    @Test
    void versionDistance() {
        MajorMinor wantedCamelVersion = new MajorMinor("2.3.0");
        assertDistance("2.3.0", "2.3.0", 0);
        assertDistance("2.3.0", "2.4.0", 1);
        List<String> sorted = Stream.of("2.3.0",
                "2.4.0",
                "2.5.0",
                "2.2.0",
                "2.1.0",
                "3.0.0",
                "3.1.0",
                "4.0.0",
                "4.1.0",
                "1.2.0",
                "1.1.0",
                "0.2.0",
                "0.1.0")
                .map(MajorMinor::new)
                .map(camelVersion -> new CamelVersionInPlatformRelease(
                        camelVersion.toString(),
                        camelVersion,
                        wantedCamelVersion.distanceTo(camelVersion),
                        new ComparableVersion(camelVersion.toString()),
                        MavenGav.fromCoordinates("io.quarkus.platform", "quarkus-camel-bom", camelVersion.toString() + ".0",
                                "pom", null),
                        RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL))
                .sorted()
                .map(CamelVersionInPlatformRelease::camelMajorMinor)
                .map(MajorMinor::toString)
                .toList();

        Assertions.assertThat(sorted).containsExactly(
                "2.3.0",
                "2.4.0",
                "2.5.0",
                "2.2.0",
                "2.1.0",
                "3.0.0",
                "3.1.0",
                "4.0.0",
                "4.1.0",
                "1.2.0",
                "1.1.0",
                "0.2.0",
                "0.1.0");
    }

    private void assertDistance(String a, String b, long expected) {
        Assertions.assertThat(new MajorMinor(a).distanceTo(new MajorMinor(b))).isEqualTo(expected);
    }
}
