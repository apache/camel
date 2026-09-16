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
package org.apache.camel.component.opa;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.styra.opa.wasm.OpaPolicy;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Evaluates the policy in-process from a WebAssembly bundle produced by {@code opa build -t wasm}.
 * <p/>
 * No OPA server is involved, so there is no network hop and no unreachable policy decision point - at the cost of the
 * policy being a build-time artefact rather than something a server distributes and updates.
 */
public class OpaWasmEvaluator extends OpaPolicyEvaluator implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String POLICY_WASM = "policy.wasm";
    private static final String DATA_JSON = "data.json";

    private final OpaWasmPolicyPool pool;
    private final String entrypoint;
    private final String data;

    public OpaWasmEvaluator(byte[] wasm, String data, String entrypoint, int poolSize, long borrowTimeout,
                            String policyPath, String allowKey, String includeHeaders, String includeProperties,
                            boolean includeBody, boolean failOpen) {
        super(policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen);
        this.entrypoint = entrypoint;
        this.data = data;
        // OpaPolicy carries mutable input/data and is not thread-safe, while a Camel producer is invoked
        // concurrently - so each exchange borrows its own instance rather than sharing one
        this.pool = new OpaWasmPolicyPool(() -> OpaPolicy.builder().withPolicy(wasm).build(), poolSize, borrowTimeout);
        // fail at startup rather than on the first exchange: the OpaPolicy constructor is what rejects a module
        // that is not a valid OPA bundle, and the pool creates instances lazily
        try (OpaWasmPolicyPool.Lease warmup = pool.borrow()) {
            prepare(warmup.policy());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading the WebAssembly policy", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out loading the WebAssembly policy", e);
        }
    }

    /**
     * Applies the entrypoint and data to a borrowed instance.
     * <p/>
     * Done on every borrow rather than once at creation, so that a pooled instance always evaluates the configured rule
     * against the bundle's data regardless of what a previous borrower left on it. See
     * {@link OpaWasmPolicyPool.Lease#close()} for why nothing is reset on return.
     */
    private void prepare(OpaPolicy policy) {
        policy.entrypoint(entrypoint);
        if (data != null) {
            policy.data(data);
        }
    }

    /**
     * Loads a WebAssembly policy from a Camel resource location.
     * <p/>
     * {@code opa build} emits a {@code bundle.tar.gz} holding {@code /policy.wasm} alongside the source and a manifest,
     * so that is what an operator will actually have to hand; a bare {@code .wasm} is accepted too.
     */
    public static Bundle loadPolicy(CamelContext camelContext, String location) throws Exception {
        try (InputStream in = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, location)) {
            byte[] content = in.readAllBytes();
            return isGzip(content) ? extractFromBundle(content, location) : new Bundle(content, null);
        }
    }

    /**
     * A loaded policy: the WebAssembly module, and the data document that {@code opa build} packed beside it when the
     * source was a bundle. A policy that reads {@code data.*} needs the latter to decide the same way it would against
     * a server that had loaded the same bundle.
     *
     * @param wasm the WebAssembly module
     * @param data the bundle's data document as JSON, or null when there was none
     */
    public record Bundle(byte[] wasm, String data) {
    }

    private static boolean isGzip(byte[] content) {
        return content.length > 1 && (content[0] & 0xff) == 0x1f && (content[1] & 0xff) == 0x8b;
    }

    private static Bundle extractFromBundle(byte[] bundle, String location) throws Exception {
        byte[] wasm = null;
        String data = null;
        try (TarArchiveInputStream tar
                = new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(bundle)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().endsWith(POLICY_WASM)) {
                    wasm = tar.readAllBytes();
                } else if (entry.getName().endsWith(DATA_JSON)) {
                    data = new String(tar.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        if (wasm == null) {
            throw new IllegalArgumentException(
                    "No " + POLICY_WASM + " inside the bundle at " + location
                                               + ". Build it with: opa build -t wasm -e <entrypoint> <policy.rego>");
        }
        return new Bundle(wasm, data);
    }

    @Override
    protected Object evaluateDecision(Map<String, Object> input) throws Exception {
        OpaWasmPolicyPool.Lease lease = pool.borrow();
        try {
            prepare(lease.policy());
            Object decision = unwrap(lease.policy().evaluate(MAPPER.writeValueAsString(input)));
            lease.close();
            return decision;
        } catch (Exception e) {
            // a trap part-way through an evaluation can leave the instance in an undefined state, so drop it
            // rather than hand it to the next exchange. Safe to call unconditionally: a lease ends exactly once,
            // so this is a no-op when close() above already returned it
            lease.discard();
            throw e;
        }
    }

    /**
     * Unwraps the WebAssembly ABI's result envelope, which is an array of result objects:
     * <code>[{"result": &lt;value&gt;}]</code>.
     * <p/>
     * An <em>empty</em> array means the rule was undefined for this input. The REST engine surfaces that as an error,
     * and this one must too: a route that aborts on an undefined decision against a server must not quietly see a deny
     * against a bundle, or the engine would be visible in the behaviour.
     */
    private Object unwrap(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalStateException(
                    "Policy " + getPolicyPath() + " returned no decision: the rule is undefined for this input."
                                            + " Give the rule a default, as in 'default allow := false'");
        }
        JsonNode result = root.get(0).get("result");
        return result == null ? null : MAPPER.convertValue(result, Object.class);
    }

    @Override
    public void close() {
        pool.close();
    }
}
