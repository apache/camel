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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointScopeTest {

    @Test
    void remoteAsTheRuntimeSaysElseInternalOrLocalByTheCatalog() {
        assertThat(EndpointScope.of("kafka://orders", true)).isEqualTo(EndpointScope.REMOTE);
        assertThat(EndpointScope.of("file://orders?noop=true", true)).isEqualTo(EndpointScope.REMOTE);
        // Camel plumbing that stays inside the integration
        assertThat(EndpointScope.of("direct://charge", false)).isEqualTo(EndpointScope.INTERNAL);
        assertThat(EndpointScope.of("log:payment", false)).isEqualTo(EndpointScope.INTERNAL);
        assertThat(EndpointScope.of("timer://tick?period=1000", false)).isEqualTo(EndpointScope.INTERNAL);
        // not remote, yet it runs a process or code on this host
        assertThat(EndpointScope.of("exec://wc", false)).isEqualTo(EndpointScope.LOCAL);
        assertThat(EndpointScope.of("bean:greeter", false)).isEqualTo(EndpointScope.LOCAL);
    }

    @Test
    void aComponentTheCatalogDoesNotKnowIsNeverInternal() {
        assertThat(EndpointScope.of("my-custom://thing", false)).isEqualTo(EndpointScope.LOCAL);
        assertThat(EndpointScope.of(null, false)).isEqualTo(EndpointScope.LOCAL);
        assertThat(EndpointScope.isInternal("Direct")).isFalse();
        assertThat(EndpointScope.isInternal("../direct")).isFalse();
    }

    @Test
    void theSchemeIsWhatComesBeforeTheFirstColon() {
        assertThat(EndpointScope.scheme("seda://queue?size=10")).isEqualTo("seda");
        assertThat(EndpointScope.scheme("log:info")).isEqualTo("log");
        assertThat(EndpointScope.scheme("direct")).isEqualTo("direct");
        assertThat(EndpointScope.scheme(null)).isNull();
    }
}
