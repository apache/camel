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

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.bouncycastle.pqc.jcajce.spec.MayoParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SnovaParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /**
     * {@code MayoParameterSpec.fromName} returns {@code null} for an unknown name rather than throwing, and
     * {@code getName()} answers the canonical form ({@code MAYO-1}) rather than echoing the requested one, so the level
     * actually resolved is pinned explicitly.
     */
    @ParameterizedTest
    @CsvSource({
            "MAYO,mayo1,MAYO-1",
            "MAYO,mayo2,MAYO-2",
            "MAYO,mayo3,MAYO-3",
            "MAYO,mayo5,MAYO-5",
            "MAYO,MAYO-1,MAYO-1",
            // BouncyCastle keeps the underscores in the SNOVA names, so these must survive as written
            "SNOVA,SNOVA_24_5_5_SHAKE_SSK,SNOVA_24_5_5_SHAKE_SSK",
            "SNOVA,snova_24_5_5_shake_ssk,SNOVA_24_5_5_SHAKE_SSK",
            "SNOVA,SNOVA_24_5_4_ESK,SNOVA_24_5_4_ESK" })
    void everyNamedLevelResolvesToTheLevelRequested(String algorithm, String requested, String canonical) {
        AlgorithmParameterSpec spec = PQCParameterSpecResolver.resolve(algorithm, requested);

        assertNotNull(spec, algorithm + " must resolve " + requested);
        String name = spec instanceof MayoParameterSpec mayo
                ? mayo.getName()
                : ((SnovaParameterSpec) spec).getName();
        assertEquals(canonical, name, "the resolved parameter set must be the level that was requested");
    }

    @Test
    void statefulAlgorithmsStillRejectAParameterSet() {
        for (String algorithm : new String[] { "XMSS", "XMSSMT", "LMS", "HSS" }) {
            assertFalse(PQCParameterSpecResolver.isSupported(algorithm),
                    algorithm + " has no name-addressable parameter spec in BouncyCastle");
        }
    }

    @Test
    void theAliasAlgorithmsAreNotReportedAsNonFips() {
        // DILITHIUM resolves to ML-DSA and SPHINCSPLUS to SLH-DSA, so warning that they are not FIPS 204/205 and
        // recommending ML-DSA / SLH-DSA would point the user at what they already use
        assertFalse(PQCProducer.NON_FIPS_PQ_SIGNATURES.contains(PQCSignatureAlgorithms.DILITHIUM.name()),
                "DILITHIUM is an ML-DSA alias and is FIPS 204");
        assertFalse(PQCProducer.NON_FIPS_PQ_SIGNATURES.contains(PQCSignatureAlgorithms.SPHINCSPLUS.name()),
                "SPHINCSPLUS is an SLH-DSA alias and is FIPS 205");
        assertTrue(PQCProducer.NON_FIPS_PQ_SIGNATURES.contains(PQCSignatureAlgorithms.FALCON.name()),
                "FALCON is genuinely not in FIPS 204/205 yet");
    }
}
