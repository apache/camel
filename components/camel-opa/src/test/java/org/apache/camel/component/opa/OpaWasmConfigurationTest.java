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

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code wasm} checks that reject a configuration before any bundle is loaded.
 * <p/>
 * These stay unit tests deliberately: they are the only two that never reach {@code loadPolicy}, so they need no
 * compiled artifact and no container. Everything that evaluates a policy lives in {@link OpaWasmIT}, where the bundle
 * is compiled from the Rego under test rather than committed beside it.
 */
public class OpaWasmConfigurationTest extends CamelTestSupport {

    @Test
    void requiresAPolicyBundle() {
        // the check runs when the endpoint starts, so a misconfiguration fails fast rather than once per message
        assertThatThrownBy(() -> template.request("opa:authz/allow?evaluationMode=wasm", e -> {
        }))
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("policyBundle is required");
    }

    @Test
    void rejectsAPoolSizeBelowOne() {
        // rejected before the bundle is resolved, so the location here is never opened
        assertThatThrownBy(() -> template.request(
                "opa:authz/allow?evaluationMode=wasm&policyBundle=file:unused.wasm&poolSize=0", e -> {
                }))
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("poolSize must be at least 1");
    }
}
