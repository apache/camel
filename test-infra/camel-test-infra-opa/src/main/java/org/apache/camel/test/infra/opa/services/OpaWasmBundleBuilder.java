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
package org.apache.camel.test.infra.opa.services;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.opa.common.OpaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.Transferable;

/**
 * Compiles Rego into the WebAssembly bundle that {@code evaluationMode=wasm} evaluates.
 * <p/>
 * This is the other half of what this module is for. The service next door runs an OPA server so the REST tests can
 * talk to it; in-process evaluation has no server to talk to, but the Rego still has to be compiled, and
 * {@code opa build -t wasm} needs the OPA binary. Rather than commit the compiled artifact - which drifts silently
 * against the {@code .rego} beside it, and has no business in a source release - a test asks for a bundle and gets one
 * built from the policy it is actually asserting on.
 * <p/>
 * The image is the one already pinned in {@code container.properties}, so the compiler and the server the REST tests
 * use never disagree about their OPA version.
 */
public final class OpaWasmBundleBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(OpaWasmBundleBuilder.class);
    private static final String WORK_DIR = "/policy";
    // the sources go to a directory the working-directory setting creates for us, but the bundle cannot be
    // written there: that directory ends up owned by root while the OPA image runs as a non-root user, so
    // "opa build" fails with "open bundle.tar.gz: permission denied". /tmp is writable, so -o points there.
    private static final String BUNDLE = "/tmp/bundle.tar.gz";

    private OpaWasmBundleBuilder() {
    }

    /**
     * Compiles a single Rego policy.
     *
     * @param  fileName    the name to give the policy inside the build, for example {@code authz.rego}
     * @param  rego        the policy source
     * @param  entrypoints the rules to expose, as {@code opa build -e} names them - an entrypoint is fixed at build
     *                     time and is not the same thing as a data path
     * @return             the {@code bundle.tar.gz} {@code opa build} emitted, holding {@code /policy.wasm}
     */
    public static byte[] build(String fileName, String rego, String... entrypoints) {
        return build(Map.of(fileName, rego.getBytes(StandardCharsets.UTF_8)), entrypoints);
    }

    /**
     * Compiles a set of sources, so a policy that reads {@code data.*} can be built together with the {@code data.json}
     * that {@code opa build} packs beside it.
     *
     * @param  sources     file name to content, for example {@code roles.rego} and {@code data.json}
     * @param  entrypoints the rules to expose
     * @return             the {@code bundle.tar.gz} {@code opa build} emitted
     */
    public static byte[] build(Map<String, byte[]> sources, String... entrypoints) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required to build a bundle");
        }
        if (entrypoints == null || entrypoints.length == 0) {
            throw new IllegalArgumentException(
                    "At least one entrypoint is required; opa build -t wasm emits nothing callable without one");
        }

        String image = LocalPropertyResolver.getProperty(OpaLocalContainerInfraService.class, OpaProperties.OPA_CONTAINER);
        LOG.info("Compiling {} to WebAssembly with {}", sources.keySet(), image);

        Map<String, byte[]> ordered = new LinkedHashMap<>(sources);
        GenericContainer<?> compiler = compiler(image, ordered, entrypoints);
        try {
            compiler.start();
            // the container has exited by now: OneShotStartupCheckStrategy waits for that rather than for a port,
            // and the bundle is read back out of the stopped container's filesystem
            return compiler.copyFileFromContainer(BUNDLE, InputStream::readAllBytes);
        } catch (Exception e) {
            // opa build reports what it disliked about the policy on stderr, and losing that leaves a caller with
            // "container did not start correctly", which says nothing about their Rego
            throw new IllegalStateException(
                    "Could not compile " + sources.keySet() + " to a WebAssembly bundle. opa said: " + logsOf(compiler),
                    e);
        } finally {
            compiler.stop();
        }
    }

    private static String logsOf(GenericContainer<?> container) {
        try {
            String logs = container.getLogs();
            return logs == null || logs.isBlank() ? "(nothing)" : logs.strip();
        } catch (Exception e) {
            return "(logs unavailable: " + e.getMessage() + ")";
        }
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> compiler(String image, Map<String, byte[]> sources, String[] entrypoints) {
        GenericContainer<?> container = new GenericContainer<>(image) // NOSONAR
                .withWorkingDirectory(WORK_DIR)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy());

        for (Map.Entry<String, byte[]> source : sources.entrySet()) {
            container.withCopyToContainer(
                    Transferable.of(source.getValue()), WORK_DIR + "/" + source.getKey());
        }
        return container.withCommand(command(entrypoints));
    }

    private static String[] command(String[] entrypoints) {
        // "opa build -t wasm -e <ep> ... ." - building the directory rather than naming the files is what makes
        // opa build pack a data.json sitting beside the policy into the bundle
        List<String> command = new ArrayList<>(List.of("build", "-t", "wasm", "-o", BUNDLE));
        for (String entrypoint : entrypoints) {
            command.add("-e");
            command.add(entrypoint);
        }
        command.add(".");
        return command.toArray(new String[0]);
    }
}
