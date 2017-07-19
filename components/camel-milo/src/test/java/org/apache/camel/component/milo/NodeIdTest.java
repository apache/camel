/**
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

import java.io.Serializable;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.junit.Test;

import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static org.apache.camel.component.milo.server.MiloServerComponent.DEFAULT_NAMESPACE_URI;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

/**
 * Testing different ways to specify node IDs
 */
public class NodeIdTest extends AbstractMiloServerTest {

    @Test
    public void testFull1() {
        final String s = String.format("nsu=%s;s=%s", DEFAULT_NAMESPACE_URI, "item-1");
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?samplingInterval=1000&node=RAW(" + s + ")", DEFAULT_NAMESPACE_URI, "item-1");
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
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?samplingInterval=1000&node=" + urlFormParameterEscaper().escape(s), ushort(1), uint(2));
    }

    @Test
    public void testDocURL() {
        testUri("milo-client://user:password@localhost:12345?node=RAW(nsu=http://foo.bar;s=foo/bar)", "http://foo.bar", "foo/bar");
    }

    @Test(expected = ResolveEndpointFailedException.class)
    public void testMixed() {
        // This must fail since "node" is incomplete
        testUri("milo-client:tcp://foo:bar@localhost:@@port@@?node=foo&namespaceUri=" + DEFAULT_NAMESPACE_URI, null, null);
    }

    private void testUri(final String uri, final Serializable namespace, final Serializable partialNodeId) {
        assertNodeId(getMandatoryEndpoint(resolve(uri), MiloClientEndpoint.class), namespace, partialNodeId);
    }

    private void assertNodeId(final MiloClientEndpoint endpoint, final Serializable namespace, final Serializable partialNodeId) {

        final NamespaceId ns = endpoint.makeNamespaceId();
        final PartialNodeId pn = endpoint.makePartialNodeId();

        assertNotNull(ns);
        assertNotNull(pn);

        assertEquals(namespace, ns.getValue());
        assertEquals(partialNodeId, pn.getValue());
    }

}
