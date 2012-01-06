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
package org.apache.camel.component.websocket;

import org.apache.camel.CamelContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

public class WebsocketConfigurationTest {

    private static final String REMAINING = "foo";
    private static final String URI = "websocket://" + REMAINING;
    private static final String PARAMETERS = "org.apache.camel.component.websocket.MemoryWebsocketStore";

    @Mock
    private WebsocketComponent component;

    @Mock
    private CamelContext camelContext;

    private WebsocketEndpoint websocketEndpoint;

    private WebsocketConfiguration wsConfig = new WebsocketConfiguration();

    @Before
    public void setUp() throws Exception {
        component = new WebsocketComponent();
        component.setCamelContext(camelContext);
    }

    @Test
    public void testParameters() throws Exception {

        assertNull(wsConfig.getGlobalStore());

        wsConfig.setGlobalStore(PARAMETERS);

        assertNotNull(wsConfig.getGlobalStore());

        websocketEndpoint = new WebsocketEndpoint(URI, component, REMAINING, wsConfig);

        assertNotNull(websocketEndpoint);
        assertNotNull(REMAINING);
        assertNotNull(wsConfig.getGlobalStore());
        // System.out.println(URI);
        // System.out.println(component);
        // System.out.println(REMAINING);
        // System.out.println(wsConfig.getGlobalStore());

    }

}
