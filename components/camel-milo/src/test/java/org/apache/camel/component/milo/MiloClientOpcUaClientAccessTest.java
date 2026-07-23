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
package org.apache.camel.component.milo;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the underlying milo {@link OpcUaClient} can be obtained from a {@link MiloClientConnection}, so that
 * its encoding contexts can be used to encode/decode custom OPC UA data types (returned as {@code ExtensionObject}).
 */
public class MiloClientOpcUaClientAccessTest extends AbstractMiloServerTest {

    private static final String MILO_SERVER_ITEM = "milo-server:myitem1";

    private static final String MILO_CLIENT
            = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?allowedSecurityPolicies=None&overrideHost=true";

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // ensures the embedded OPC UA server is up with a registered item
                from("direct:start").to(MILO_SERVER_ITEM);
            }
        };
    }

    @Test
    void shouldExposeOpcUaClientAndItsEncodingContext() throws Exception {
        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            // the connection is established lazily and asynchronously
            await().atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertNotNull(connection.getOpcUaClient(), "OpcUaClient should be exposed"));

            final OpcUaClient client = connection.getOpcUaClient();
            assertNotNull(client);

            // the whole point of the accessor: reaching the encoding context to (de)serialize ExtensionObject values,
            // here exercised with a built-in structured type to avoid depending on a server-defined custom type
            final EncodingContext encodingContext = client.getStaticEncodingContext();
            assertNotNull(encodingContext);

            final ReadValueId original
                    = new ReadValueId(NodeId.NULL_VALUE, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
            final ExtensionObject encoded = ExtensionObject.encode(encodingContext, original);
            final UaStructuredType decoded = encoded.decode(encodingContext);

            assertInstanceOf(ReadValueId.class, decoded);
        } finally {
            endpoint.releaseConnection(connection);
        }
    }
}
