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
package org.apache.camel.component.gae.login;

import org.junit.Test;

import static org.apache.camel.component.gae.login.GLoginTestUtils.createEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GLoginEndpointTest {

    @Test
    public void testEndpointProperties() throws Exception {
        // test internet hostname
        StringBuilder buffer = new StringBuilder("glogin:test.appspot.com")
            .append("?").append("clientName=testClientName")
            .append("&").append("userName=testUserName")
            .append("&").append("password=testPassword");
        GLoginEndpoint endpoint = createEndpoint(buffer.toString());
        assertEquals("test.appspot.com", endpoint.getHostName());
        assertEquals("testClientName", endpoint.getClientName());
        assertEquals("testUserName", endpoint.getUserName());
        assertEquals("testPassword", endpoint.getPassword());
        assertFalse(endpoint.isDevMode());
        endpoint = createEndpoint("glogin:test.appspot.com");
        assertEquals("apache-camel-2.x", endpoint.getClientName());
        // test localhost with default port
        endpoint = createEndpoint("glogin:localhost?devMode=true&devAdmin=true");
        assertEquals("localhost", endpoint.getHostName());
        assertEquals(8080, endpoint.getDevPort());
        assertTrue(endpoint.isDevMode());
        assertTrue(endpoint.isDevAdmin());
        // test localhost with custom port
        endpoint = createEndpoint("glogin:localhost:9090?devMode=true");
        assertEquals("localhost", endpoint.getHostName());
        assertEquals(9090, endpoint.getDevPort());
        assertFalse(endpoint.isDevAdmin());
    }
    
}
