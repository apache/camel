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
package org.apache.camel.component.spiffe;

import io.spiffe.spiffeid.SpiffeId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuration-time (beforeWrap) validation and the authorization decision, exercised without a running route.
 */
class SpiffeSecurityPolicyConfigTest {

    @Test
    void rejectsBothAcceptAnyAndAnAllowList() {
        SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy("spiffe://example.org/frontend");
        policy.setAcceptAnySpiffeId(true);

        assertThatThrownBy(() -> policy.beforeWrap(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void failsClosedWhenNeitherIsConfigured() {
        SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy();

        assertThatThrownBy(() -> policy.beforeWrap(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires either");
    }

    @Test
    void rejectsAnAllowListThatIsAllWhitespace() {
        SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy(" , ,  ");

        assertThatThrownBy(() -> policy.beforeWrap(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not contain any SPIFFE ID");
    }

    @Test
    void allowListAuthorizesOnlyTheListedPeers() {
        SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy("spiffe://example.org/frontend, spiffe://example.org/ops");
        policy.beforeWrap(null, null);

        assertThat(policy.isAuthorized(SpiffeId.parse("spiffe://example.org/frontend"))).isTrue();
        assertThat(policy.isAuthorized(SpiffeId.parse("spiffe://example.org/ops"))).isTrue();
        assertThat(policy.isAuthorized(SpiffeId.parse("spiffe://example.org/backend"))).isFalse();
        assertThat(policy.isAuthorized(SpiffeId.parse("spiffe://other.org/frontend"))).isFalse();
    }

    @Test
    void acceptAnyAuthorizesEveryParsedPeer() {
        SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy();
        policy.setAcceptAnySpiffeId(true);
        policy.beforeWrap(null, null);

        assertThat(policy.isAuthorized(SpiffeId.parse("spiffe://example.org/frontend"))).isTrue();
        assertThat(policy.isAuthorized(SpiffeId.parse("spiffe://any.other/workload"))).isTrue();
    }
}
