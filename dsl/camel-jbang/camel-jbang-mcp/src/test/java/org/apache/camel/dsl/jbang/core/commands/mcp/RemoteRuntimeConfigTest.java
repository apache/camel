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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteRuntimeConfigTest {

    @Test
    void authorizesOnlyTheConfiguredBearerToken() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.token = "a-test-token-with-enough-randomness";

        assertThat(config.isAuthorized("Bearer a-test-token-with-enough-randomness")).isTrue();
        assertThat(config.isAuthorized("Bearer wrong-token")).isFalse();
        assertThat(config.isAuthorized(null)).isFalse();
    }

    @Test
    void ownerNamespaceDoesNotContainTheBearerToken() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.token = "a-test-token-with-enough-randomness";

        assertThat(config.ownerId()).doesNotContain(config.token).startsWith("token:");
    }

    @Test
    void doesNotAuthorizeRequestsWhenTheTokenIsUnconfigured() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.token = "   ";

        assertThat(config.isTokenConfigured()).isFalse();
        assertThat(config.isAuthorized("Bearer any-token")).isFalse();
    }

}
