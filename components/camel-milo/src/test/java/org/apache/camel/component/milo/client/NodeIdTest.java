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
package org.apache.camel.component.milo.client;

import java.io.Serializable;

import com.google.common.net.UrlEscapers;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.milo.AbstractMiloServerTest;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testing different ways to specify node IDs
 */
public class NodeIdTest extends AbstractMiloServerTest {

    private static final Logger LOG = LoggerFactory.getLogger(NodeIdTest.class);

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Test
    public void testFull1() {
        final String s = String.format("nsu=%s;s=%s", MiloServerComponent.DEFAULT_NAMESPACE_URI, "item-1");
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?samplingInterval=1000&node=RAW(" + s + ")",
                MiloServerComponent.DEFAULT_NAMESPACE_URI,
                "item-1");
    }

    @Test
    public void testFull2() {
        final String s = String.format("ns=%s;s=%s", 1, "item-1");
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?samplingInterval=1000&node=RAW(" + s + ")", ushort(1), "item-1");
    }

    @Test
    public void testFull3() {
        final String s = String.format("ns=%s;i=%s", 1, 2);
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?samplingInterval=1000&node=RAW(" + s + ")", ushort(1), uint(2));
    }

    @Test
    public void testFull1NonRaw() {
        final String s = String.format("ns=%s;i=%s", 1, 2);
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?samplingInterval=1000&node="
                + UrlEscapers.urlFormParameterEscaper().escape(s), ushort(1), uint(2));
    }

    @Test
    public void testDocURL() {
        testUri("milo-client://user:password@localhost:12345?node=RAW(nsu=http://foo.bar;s=foo/bar)", "http://foo.bar",
                "foo/bar");
    }

    @Test
    public void testMixed() {
        // This must fail since "node" is incomplete
        assertThrows(ResolveEndpointFailedException.class,
                () -> testUri("milo-client:tcp://foo:bar@localhost:@@port@@?node=foo&namespaceUri="
                              + MiloServerComponent.DEFAULT_NAMESPACE_URI,
                        null, null));
    }

    private void testUri(final String uri, final Serializable namespace, final Serializable partialNodeId) {
        assertNodeId(getMandatoryEndpoint(resolve(uri), MiloClientEndpoint.class), namespace, partialNodeId);
    }

    private void assertNodeId(
            final MiloClientEndpoint endpoint, final Serializable namespace, final Serializable partialNodeId) {

        final ExpandedNodeId en = endpoint.getNodeId();

        assertNotNull(en);

        assertEquals(namespace, en.getNamespaceUri() == null ? en.getNamespaceIndex() : en.getNamespaceUri());
        assertEquals(partialNodeId, en.getIdentifier());
    }

}
