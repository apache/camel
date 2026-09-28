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
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpenFgaEndpointConfigurationTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private OpenFgaEndpoint endpoint(String uri) throws Exception {
        OpenFgaEndpoint endpoint = context.getEndpoint(uri, OpenFgaEndpoint.class);
        endpoint.start();
        return endpoint;
    }

    @Test
    void readsTheOperationFromTheUriPath() throws Exception {
        OpenFgaEndpoint endpoint = endpoint("openfga:listObjects?openFgaClient=#fgaClient&storeId=" + STORE
                                            + "&user=user:anne&relation=reader&type=document");

        assertThat(endpoint.getOperation()).isEqualTo(OpenFgaOperation.listObjects);
    }

    @Test
    void namesTheOperationsThatExistWhenGivenOneThatDoesNot() {
        assertThatThrownBy(() -> context.getEndpoint("openfga:checkIt?storeId=" + STORE, OpenFgaEndpoint.class))
                // the enum's own "No enum constant ..." names the Java type and leaves the reader to guess the
                // spelling, so the component adds a message that lists the operations there are
                .hasStackTraceContaining("Unknown operation 'checkIt'")
                .hasStackTraceContaining("writeTuples");
    }

    @Test
    void requiresAnOperation() {
        assertThatThrownBy(() -> context.getEndpoint("openfga:?storeId=" + STORE, OpenFgaEndpoint.class))
                .rootCause()
                .hasMessageContaining("An operation must be given");
    }

    @Test
    void refusesACheckWithNothingToCheck() {
        assertThatThrownBy(() -> endpoint("openfga:check?openFgaClient=#fgaClient&storeId=" + STORE + "&user=user:anne"))
                .rootCause()
                // a missing relation would otherwise surface on the first exchange as a deny, which looks exactly
                // like a policy decision and is thoroughly misleading to debug
                .hasMessageContaining("relation is required for the check operation");
    }

    @Test
    void refusesAListObjectsWithNoTypeToEnumerate() {
        assertThatThrownBy(() -> endpoint("openfga:listObjects?openFgaClient=#fgaClient&storeId=" + STORE
                                          + "&user=user:anne&relation=reader"))
                .rootCause()
                .hasMessageContaining("type is required for the listObjects operation");
    }

    @Test
    void refusesAListRelationsWithNoRelationsToAskAbout() {
        assertThatThrownBy(() -> endpoint("openfga:listRelations?openFgaClient=#fgaClient&storeId=" + STORE
                                          + "&user=user:anne&object=document:budget"))
                .rootCause()
                .hasMessageContaining("relations is required for the listRelations operation");
    }

    @Test
    void letsTheTupleOperationsTakeEverythingFromTheMessage() throws Exception {
        // writeTuples and deleteTuples read their tuples from the body, so nothing has to be configured up front
        OpenFgaEndpoint endpoint = endpoint("openfga:writeTuples?openFgaClient=#fgaClient&storeId=" + STORE);

        assertThat(endpoint.getOperation()).isEqualTo(OpenFgaOperation.writeTuples);
    }

    @Test
    void doesNotSupportConsumingFromOpenFga() throws Exception {
        OpenFgaEndpoint endpoint = endpoint("openfga:check?openFgaClient=#fgaClient&storeId=" + STORE
                                            + "&user=user:anne&relation=reader&object=document:budget");

        assertThatThrownBy(() -> endpoint.createConsumer(e -> {
        })).isInstanceOf(UnsupportedOperationException.class);
    }
}
