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
package org.apache.camel.component.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for how {@link OpenFgaAuthorizer} bounds the wait on a request.
 */
class OpenFgaAuthorizerTest {

    private final OpenFgaClient client = mock(OpenFgaClient.class);
    private final CamelContext camelContext = mock(CamelContext.class);

    @Test
    void batchAwaitTimeoutScalesWithTheNumberOfFanOutWaves() {
        OpenFgaConfiguration configuration = new OpenFgaConfiguration();
        configuration.setConnectTimeout(10000);
        configuration.setReadTimeout(10000);
        configuration.setMaxRetries(3);
        OpenFgaAuthorizer authorizer = new OpenFgaAuthorizer(client, configuration, camelContext);

        // connect + read, per attempt, across the first try and the three retries
        long perRequest = (10000L + 10000L) * (3 + 1);
        // AWAIT_SAFETY_MARGIN_MILLIS, added once however many waves the batch fans out into
        long margin = 5000L;

        // a batch that fits in a single wave is bounded like a single request: the per-request time plus the margin
        assertThat(authorizer.batchAwaitTimeoutMillis(10, 10)).isEqualTo(perRequest + margin);
        assertThat(authorizer.batchAwaitTimeoutMillis(5, 10)).isEqualTo(perRequest + margin);
        // more objects than can run in parallel fan out into ceil(objectCount / maxParallelRequests) waves, and the
        // bound grows a whole per-request budget with each extra wave - while the margin is still added only once
        assertThat(authorizer.batchAwaitTimeoutMillis(11, 10)).isEqualTo(perRequest * 2 + margin);
        assertThat(authorizer.batchAwaitTimeoutMillis(25, 10)).isEqualTo(perRequest * 3 + margin);
        // defensive: a non-positive parallelism is clamped to one in-flight request rather than dividing by zero
        assertThat(authorizer.batchAwaitTimeoutMillis(5, 0)).isEqualTo(perRequest * 5 + margin);
        // an empty batch still bounds a single wave rather than collapsing to just the margin
        assertThat(authorizer.batchAwaitTimeoutMillis(0, 10)).isEqualTo(perRequest + margin);
    }
}
