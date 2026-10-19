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
package org.apache.camel.component.pqc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the consistency rules the sibling key material classes are meant to follow but drifted away from, so the next
 * clone-and-tweak does not reintroduce the divergence.
 */
class PQCMaterialConsistencyTest {

    private static Path materialSourceRoot() throws URISyntaxException {
        // the tests run from the module directory, so the sources sit at a fixed relative path
        Path root = Paths.get("src/main/java/org/apache/camel/component/pqc/crypto");
        assertTrue(Files.isDirectory(root), "cannot locate the key material sources at " + root.toAbsolutePath());
        return root;
    }

    private static List<Path> materialSources() throws Exception {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(materialSourceRoot())) {
            files.filter(p -> p.getFileName().toString().startsWith("PQCDefault"))
                    .filter(p -> p.getFileName().toString().endsWith("Material.java"))
                    .forEach(sources::add);
        }
        assertFalse(sources.isEmpty(), "no key material sources found");
        return sources;
    }

    @Test
    void everyKeyMaterialSeedsItsGeneratorWithSecureRandomHelper() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path source : materialSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (!text.contains("SecureRandomHelper.getSecureRandom()")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertTrue(offenders.isEmpty(),
                "these key material classes fall back to the provider default RNG instead of the shared"
                                        + " SecureRandomHelper: " + offenders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "MAYO", "SNOVA" })
    void mayoAndSnovaAcceptANamedParameterSet(String algorithm) {
        assertTrue(PQCParameterSpecResolver.isSupported(algorithm),
                algorithm + " has a name-addressable BouncyCastle parameter spec and must accept parameterSpec");
    }

    @Test
    void mayoResolvesEveryDocumentedLevel() {
        for (String name : new String[] { "mayo1", "mayo2", "mayo3", "mayo5" }) {
            assertTrue(PQCParameterSpecResolver.resolve("MAYO", name).toString().toLowerCase().contains("mayo"),
                    "MAYO must resolve " + name);
        }
    }

    @Test
    void statefulAlgorithmsStillRejectAParameterSet() {
        for (String algorithm : new String[] { "XMSS", "XMSSMT", "LMS", "HSS" }) {
            assertFalse(PQCParameterSpecResolver.isSupported(algorithm),
                    algorithm + " has no name-addressable parameter spec in BouncyCastle");
        }
    }

    @Test
    void theAliasAlgorithmsAreNotReportedAsNonFips() throws IOException {
        // DILITHIUM resolves to ML-DSA and SPHINCSPLUS to SLH-DSA, so warning that they are not FIPS 204/205 and
        // recommending ML-DSA / SLH-DSA would point the user at what they already use
        String producer = Files.readString(
                Paths.get("src/main/java/org/apache/camel/component/pqc/PQCProducer.java"), StandardCharsets.UTF_8);
        int from = producer.indexOf("NON_FIPS_PQ_SIGNATURES = Set.of(");
        assertTrue(from > 0, "cannot locate the non-FIPS signature set");
        String block = producer.substring(from, producer.indexOf(");", from));

        assertFalse(block.contains("DILITHIUM"), "DILITHIUM is an ML-DSA alias and is FIPS 204");
        assertFalse(block.contains("SPHINCSPLUS"), "SPHINCSPLUS is an SLH-DSA alias and is FIPS 205");
        assertTrue(block.contains("FALCON"), "FALCON is genuinely not in FIPS 204/205 yet");
    }
}
