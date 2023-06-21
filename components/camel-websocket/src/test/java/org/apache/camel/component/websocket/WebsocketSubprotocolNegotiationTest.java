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
package org.apache.camel.component.websocket;

import java.util.*;
import java.util.stream.Stream;

import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.eclipse.jetty.websocket.api.util.WebSocketConstants.SEC_WEBSOCKET_PROTOCOL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;

public class WebsocketSubprotocolNegotiationTest {
    /*
     * "selected" protocols are expected to be chosen
     */
    static Stream<Arguments> subprotocolNegotiationProvider() {
        return Stream.of(
                // server supported, client proposed, actually selected, reject connection?
                arguments(null, null, null, false),                                                     // select no specified protocol
                arguments(null, "", null, false),                                                       // select no specified protocol
                arguments("", null, null, false),                                                       // select no specified protocol
                arguments("", "", null, false),                                                         // select no specified protocol
                arguments("any", null, null, false),                                                    // select no specified protocol
                arguments(null, "myProtocol", null, false),                                             // select no specified protocol
                arguments("any", "protocol1,protocol2", null, false),                                   // select no specified protocol
                arguments("   protocol1   ", "   protocol2    ,    protocol1  ", "protocol1", false),   // strip whitespace
                arguments("supported1,supported2", "supported2,supported1", "supported1", false),       // select first server supported
                arguments("supported1,supported2", "supported1,supported2", "supported1", false),       // select first server supported
                arguments("supported2,supported1", "supported1,supported2", "supported2", false),       // select first server supported
                arguments("supported2,supported1", "supported2,supported1", "supported2", false),       // select first server supported
                arguments("supported1,any", "supported1", "supported1", false),                         // select first server supported
                arguments("supported1,any", "supported2", null, false),                                 // select no specified protocol
                arguments("supported1,supported2,supported3", "supported2", "supported2", false),       // select client proposed
                arguments("supported2", "supported1,supported2,supported3", "supported2", false),       // select client proposed
                arguments("supported1", "supported2", null, true),                                      // should refuse connection
                arguments("supported1,supported2", "supported3,supported4", null, true),                // should refuse connection
                arguments("supported1,supported2,any", "supported3,supported4", null, false),           // select no specified protocol
                arguments("supported1,supported2,any", "supported3,supported2", "supported2", false)    // select second server supported
        );
    }

    /*
     * "selected" protocols are NOT expected to be chosen even though the connection is accepted
     */
    static Stream<Arguments> subprotocolNegotiationNegativeProvider() {
        return Stream.of(
                // server supported, client proposed, actually selected
                arguments(null, "myProtocol", null),                                // "myProtocol" should have been selected
                arguments(null, "protocol1 ", "protocol1 "),                        // whitespace should have been stripped from selected protocol
                arguments("serverProtocol", "clientProtocol", "serverProtocol"),    // no specific protocol should have been selected
                arguments("serverProtocol", "clientProtocol", "clientProtocol")     // no specific protocol should have been selected
        );
    }

    /*
     * Test that the correct subprotocol is selected, based on the server-supported and client-proposed lists of subprotocols
     */
    @ParameterizedTest
    @MethodSource("subprotocolNegotiationProvider")
    void testSubprotocolNegotiation(
            String supportedSubprotocols, String proposedSubprotocols, String expectedSelectedSubprotocol,
            boolean expectRejectedConnection) {
        // mock the test component inputs
        NodeSynchronization sync = mock(NodeSynchronization.class);
        Map<String, WebSocketFactory> factoryMap = mock(Map.class);
        JettyServerUpgradeRequest req = mock(JettyServerUpgradeRequest.class);
        JettyServerUpgradeResponse res = mock(JettyServerUpgradeResponse.class);
        WebsocketConsumer consumer = mock(WebsocketConsumer.class);
        WebsocketEndpoint endpoint = mock(WebsocketEndpoint.class);
        DefaultWebsocket implementation = mock(DefaultWebsocket.class);

        // return the server supported subprotocols to WebWebsocketComponentServlet, when they are asked for
        when(consumer.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getSubprotocol()).thenReturn(supportedSubprotocols);

        // return the client subprotocol proposal to WebsocketComponentServlet, when it is asked for
        List<String> proposedList = proposedSubprotocols == null ? null : new ArrayList(Arrays.asList(proposedSubprotocols));
        when(req.getHeaders(SEC_WEBSOCKET_PROTOCOL)).thenReturn(proposedList);

        // mock the factory returned from factoryMap -- we don't care about this for this test, so just make it work
        WebSocketFactory factory = mock(WebSocketFactory.class);
        when(factoryMap.get(WebsocketComponentServlet.UNSPECIFIED_SUBPROTOCOL)).thenReturn(factory);
        when(factoryMap.get(expectedSelectedSubprotocol)).thenReturn(factory);
        when(factory.newInstance(any(), any(), any(), any(), any(), any())).thenReturn(implementation);

        // this is the core functionality we are testing
        WebsocketComponentServlet wcs = new WebsocketComponentServlet(sync, "/anypath", factoryMap);
        wcs.setConsumer(consumer);
        DefaultWebsocket chosenImplementation = wcs.doWebSocketConnect(req, res);

        // verify the connection was accepted/rejected as expected
        assertEquals(chosenImplementation == null, expectRejectedConnection);

        // verify the negotiated subprotocol
        if (expectedSelectedSubprotocol == null) {
            // verify that the response subprotocol header was never set
            verify(res, never()).setHeader(eq(SEC_WEBSOCKET_PROTOCOL), anyString());
        } else {
            // verify that the subprotocol returned to the client was the one we expected
            verify(res).setHeader(SEC_WEBSOCKET_PROTOCOL, expectedSelectedSubprotocol);
        }
    }

    /*
     * Test that the specified subprotocol is NOT selected even though the connection was accepted
     */
    @ParameterizedTest
    @MethodSource("subprotocolNegotiationNegativeProvider")
    void testSubprotocolNegotiationNegative(
            String supportedSubprotocols, String proposedSubprotocols, String expectedNotSelectedSubprotocol) {
        assertThrows(AssertionError.class, () -> {
            testSubprotocolNegotiation(supportedSubprotocols, proposedSubprotocols, expectedNotSelectedSubprotocol, true);
        });
    }

}
