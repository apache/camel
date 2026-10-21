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

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.milo.browse.MiloBrowseComponent;
import org.apache.camel.component.milo.browse.MiloBrowseEndpoint;
import org.apache.camel.component.milo.client.MiloClientComponent;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reconnect timeout is the delay between two connection attempts, and not the timeout of the requests.
 */
public class ReconnectTimeoutConfigurationTest extends CamelTestSupport {

    private static final String SERVER = "opc.tcp://localhost:4840";

    @Test
    void endpointsWithoutTheOptionWaitTenSecondsBetweenTwoConnectionAttempts() {
        assertEquals(10_000L,
                context.getEndpoint("milo-client:" + SERVER, MiloClientEndpoint.class).getConfiguration()
                        .getReconnectTimeout());
        assertEquals(10_000L,
                context.getEndpoint("milo-browse:" + SERVER, MiloBrowseEndpoint.class).getConfiguration()
                        .getReconnectTimeout());
    }

    @Test
    void endpointUriSetsTheReconnectTimeout() {
        final MiloClientEndpoint client
                = context.getEndpoint("milo-client:" + SERVER + "?reconnectTimeout=1500", MiloClientEndpoint.class);
        assertEquals(1_500L, client.getConfiguration().getReconnectTimeout());
        assertNull(client.getConfiguration().getRequestTimeout());

        final MiloBrowseEndpoint browse
                = context.getEndpoint("milo-browse:" + SERVER + "?reconnectTimeout=1500", MiloBrowseEndpoint.class);
        assertEquals(1_500L, browse.getConfiguration().getReconnectTimeout());
        assertNull(browse.getConfiguration().getRequestTimeout());
    }

    @Test
    void clientComponentSetsTheDefaultReconnectTimeoutOfItsEndpoints() {
        final MiloClientComponent component = context.getComponent("milo-client", MiloClientComponent.class);
        component.setReconnectTimeout(2_000L);

        assertEquals(2_000L, component.getConfiguration().getReconnectTimeout());
        assertNull(component.getConfiguration().getRequestTimeout());

        final MiloClientEndpoint endpoint = context.getEndpoint("milo-client:" + SERVER, MiloClientEndpoint.class);
        assertEquals(2_000L, endpoint.getConfiguration().getReconnectTimeout());
        assertNull(endpoint.getConfiguration().getRequestTimeout());
    }

    @Test
    void browseComponentSetsTheDefaultReconnectTimeoutOfItsEndpoints() {
        final MiloBrowseComponent component = context.getComponent("milo-browse", MiloBrowseComponent.class);
        component.setReconnectTimeout(2_000L);

        assertEquals(2_000L, component.getConfiguration().getReconnectTimeout());
        assertNull(component.getConfiguration().getRequestTimeout());

        final MiloBrowseEndpoint endpoint = context.getEndpoint("milo-browse:" + SERVER, MiloBrowseEndpoint.class);
        assertEquals(2_000L, endpoint.getConfiguration().getReconnectTimeout());
        assertNull(endpoint.getConfiguration().getRequestTimeout());
    }

    @Test
    void reconnectTimeoutThatIsNotPositiveIsRejected() {
        // no delay would make the client try to connect again and again, as fast as the server refuses
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("milo-client:" + SERVER + "?reconnectTimeout=0"));

        final MiloClientComponent component = context.getComponent("milo-client", MiloClientComponent.class);
        assertThrows(IllegalArgumentException.class, () -> component.setReconnectTimeout(-1L));
    }
}
