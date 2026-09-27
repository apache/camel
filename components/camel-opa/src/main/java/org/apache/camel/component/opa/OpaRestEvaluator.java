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

import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAResult;
import org.apache.camel.util.ObjectHelper;

/**
 * Evaluates the policy by calling a running OPA server over its REST Data API.
 */
public class OpaRestEvaluator extends OpaPolicyEvaluator implements AutoCloseable {

    private final OPAClient client;
    private final OpaHttpClient transport;

    public OpaRestEvaluator(OPAClient client, OpaHttpClient transport, String policyPath, String allowKey,
                            String includeHeaders,
                            String includeProperties, boolean includeBody, boolean failOpen) {
        super(policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen);
        this.client = ObjectHelper.notNull(client, "client");
        this.transport = transport;
    }

    /**
     * Creates the HTTP transport for an OPA server connection.
     *
     * @param bearerToken       token for OPA API authentication, or null when OPA does not require one
     * @param connectionTimeout how long to wait for the connection to be established, in milliseconds
     * @param requestTimeout    how long to wait for the decision once connected, in milliseconds
     * @param sslContext        TLS configuration for the connection, or null for the JVM default
     */
    public static OpaHttpClient createTransport(
            String bearerToken, long connectionTimeout, long requestTimeout, SSLContext sslContext) {
        return new OpaHttpClient(connectionTimeout, requestTimeout, sslContext, bearerToken);
    }

    /**
     * Creates a client for an OPA server, over a transport this component controls.
     * <p/>
     * The SDK's own default transport builds a fresh {@code HttpClient} per request with no timeouts at all; see
     * {@link OpaHttpClient} for why neither is acceptable on the path that decides authorization.
     *
     * @param serverUrl base URL of the OPA server, without the /v1/data suffix
     * @param transport the HTTP transport to use for all requests
     */
    public static OPAClient createClient(String serverUrl, OpaHttpClient transport) {
        return new OPAClient(serverUrl, transport);
    }

    @Override
    public void close() throws Exception {
        if (transport != null) {
            transport.close();
        }
    }

    @Override
    protected Object evaluateDecision(Map<String, Object> input) throws Exception {
        // the SDK reports an undefined decision as an exception, which the base turns into a fail-closed error;
        // the WASM engine is made to behave identically
        return client.evaluate(getPolicyPath(), input, Object.class);
    }

    @Override
    protected Map<String, BatchElement> evaluateBatchDecisions(Map<String, Map<String, Object>> inputs)
            throws Exception {
        // one HTTP round-trip via OPA's batch endpoint; the SDK falls back to sequential calls if the server does
        // not implement it. Each entry carries its own decision or its own failure, so one bad element does not sink
        // the batch.
        Map<String, Object> batchInputs = new LinkedHashMap<>(inputs);
        Map<String, OPAResult> results = client.evaluateBatch(getPolicyPath(), batchInputs);
        Map<String, BatchElement> outcomes = new LinkedHashMap<>();
        for (Map.Entry<String, OPAResult> entry : results.entrySet()) {
            OPAResult result = entry.getValue();
            if (result != null && result.success()) {
                outcomes.put(entry.getKey(), new BatchElement(result.getValue(), null));
            } else {
                Exception failure = result != null ? result.getException() : null;
                if (failure == null) {
                    failure = new IllegalStateException("no result returned for batch element " + entry.getKey());
                }
                outcomes.put(entry.getKey(), new BatchElement(null, failure));
            }
        }
        return outcomes;
    }
}
