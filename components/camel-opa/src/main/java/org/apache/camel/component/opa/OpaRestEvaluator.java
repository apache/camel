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

import java.util.Map;

import com.styra.opa.OPAClient;
import org.apache.camel.util.ObjectHelper;

/**
 * Evaluates the policy by calling a running OPA server over its REST Data API.
 */
public class OpaRestEvaluator extends OpaPolicyEvaluator {

    private final OPAClient client;

    public OpaRestEvaluator(OPAClient client, String policyPath, String allowKey, String includeHeaders,
                            String includeProperties, boolean includeBody, boolean failOpen) {
        super(policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen);
        this.client = ObjectHelper.notNull(client, "client");
    }

    /**
     * Creates a client for an OPA server, optionally authenticating with a bearer token.
     *
     * @param serverUrl   base URL of the OPA server, without the /v1/data suffix
     * @param bearerToken token for OPA API authentication, or null when OPA does not require one
     */
    public static OPAClient createClient(String serverUrl, String bearerToken) {
        if (ObjectHelper.isNotEmpty(bearerToken)) {
            return new OPAClient(serverUrl, Map.of("Authorization", "Bearer " + bearerToken));
        }
        return new OPAClient(serverUrl);
    }

    @Override
    protected Object evaluateDecision(Map<String, Object> input) throws Exception {
        // the SDK reports an undefined decision as an exception, which the base turns into a fail-closed error;
        // the WASM engine is made to behave identically
        return client.evaluate(getPolicyPath(), input, Object.class);
    }
}
