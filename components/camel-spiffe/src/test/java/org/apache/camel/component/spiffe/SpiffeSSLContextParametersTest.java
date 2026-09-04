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

import java.util.Set;

import io.spiffe.spiffeid.SpiffeId;
import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpiffeSSLContextParametersTest {

    @Test
    void createSSLContextFailsClosedWithoutTrustConfig() {
        SpiffeSSLContextParameters params = new SpiffeSSLContextParameters();
        // neither acceptAnySpiffeId nor acceptedSpiffeIds set -> must refuse to build a context
        assertThatThrownBy(() -> params.createSSLContext(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acceptAnySpiffeId");
    }

    @Test
    void acceptedSpiffeIdsAreParsedAndBlanksDropped() {
        Set<SpiffeId> ids = SpiffeSSLContextParameters.parseSpiffeIds(
                "spiffe://example.org/a, , spiffe://example.org/b");
        assertThat(ids).extracting(SpiffeId::toString)
                .containsExactly("spiffe://example.org/a", "spiffe://example.org/b");
    }

    @Test
    void createSSLContextWrapsAnUnreachableWorkloadApi() {
        SpiffeSSLContextParameters params = new SpiffeSSLContextParameters();
        params.setAcceptAnySpiffeId(true);
        // an unsupported scheme fails synchronously while parsing the endpoint address (no network wait)
        params.setSpiffeSocketPath("http://localhost");
        assertThatThrownBy(() -> params.createSSLContext(null))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("X509Source");
    }

    @Test
    void acceptAnyAndAllowListAreMutuallyExclusive() {
        SpiffeSSLContextParameters params = new SpiffeSSLContextParameters();
        params.setAcceptAnySpiffeId(true);
        params.setAcceptedSpiffeIds("spiffe://example.org/a");
        assertThatThrownBy(() -> params.createSSLContext(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void blankOnlyAcceptedIdsAreRejected() {
        SpiffeSSLContextParameters params = new SpiffeSSLContextParameters();
        params.setAcceptedSpiffeIds(", ,");
        assertThatThrownBy(() -> params.createSSLContext(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not contain any SPIFFE ID");
    }
}
