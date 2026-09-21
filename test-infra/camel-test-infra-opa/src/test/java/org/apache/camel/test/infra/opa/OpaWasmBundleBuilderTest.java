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
package org.apache.camel.test.infra.opa;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.test.infra.opa.services.OpaWasmBundleBuilder;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "skipITs", matches = "true")
public class OpaWasmBundleBuilderTest {

    private static final String REGO = """
            package authz

            default allow := false

            allow if {
                input.user == "alice"
            }
            """;

    private List<String> entries(byte[] bundle) throws Exception {
        List<String> names = new ArrayList<>();
        try (TarArchiveInputStream tar
                = new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(bundle)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName().replaceFirst("^/", ""));
                }
            }
        }
        return names;
    }

    @Test
    void buildsABundleCarryingTheCompiledModule() throws Exception {
        byte[] bundle = OpaWasmBundleBuilder.build("authz.rego", REGO, "authz/allow");

        assertTrue(entries(bundle).contains("policy.wasm"),
                "opa build must emit /policy.wasm - that is the artifact the component loads");
    }

    @Test
    void packsADataDocumentSittingBesideThePolicy() throws Exception {
        // building the directory rather than naming the file is what makes opa build include data.json; a policy
        // reading data.* is useless without it
        byte[] bundle = OpaWasmBundleBuilder.build(
                Map.of("roles.rego", REGO.getBytes(StandardCharsets.UTF_8),
                        "data.json", "{\"admins\":[\"carol\"]}".getBytes(StandardCharsets.UTF_8)),
                "authz/allow");

        List<String> entries = entries(bundle);
        assertTrue(entries.contains("policy.wasm"), entries.toString());
        assertTrue(entries.contains("data.json"), entries.toString());
    }

    @Test
    void refusesToBuildWithoutAnEntrypoint() {
        // opa build -t wasm with no -e compiles happily and emits nothing callable, which would surface much
        // later as an empty result array rather than as a build failure
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> OpaWasmBundleBuilder.build("authz.rego", REGO));

        assertTrue(e.getMessage().contains("entrypoint"), e.getMessage());
    }

    @Test
    void refusesToBuildWithoutSources() {
        assertThrows(IllegalArgumentException.class, () -> OpaWasmBundleBuilder.build(Map.of(), "authz/allow"));
    }

    @Test
    void reportsWhatFailedWhenTheRegoIsNotValid() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OpaWasmBundleBuilder.build("broken.rego", "this is not rego at all", "authz/allow"));

        assertEquals(true, e.getMessage().contains("broken.rego"), e.getMessage());
    }
}
