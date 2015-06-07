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
package org.apache.camel.component.hipchat;

import java.util.Arrays;
import java.util.HashMap;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HipchatComponentTest {

    @Test
    public void testUriParseNoPort() throws Exception {
        HipchatComponent component = new HipchatComponent(Mockito.mock(CamelContext.class));
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("authToken", "token");
        parameters.put("protocol", "https");
        HipchatEndpoint endpoint = (HipchatEndpoint)component.createEndpoint("hipchat://localhost?authToken=token&protocol=https", "localhost", parameters);
        assertEquals("localhost", endpoint.getConfiguration().getHost());
        assertEquals(new Integer(80), endpoint.getConfiguration().getPort());
        assertEquals("https", endpoint.getConfiguration().getProtocol());
        assertEquals("token", endpoint.getConfiguration().getAuthToken());
        assertEquals("https://localhost:80", endpoint.getConfiguration().hipChatUrl());
        assertEquals("/a?auth_token=token", endpoint.getConfiguration().withAuthToken("/a"));
    }

    @Test
    public void testUriParseFull() throws Exception {
        HipchatComponent component = new HipchatComponent(Mockito.mock(CamelContext.class));
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("authToken", "token");
        parameters.put("protocol", "https");
        parameters.put("consumeUsers", "@auser,@buser");
        HipchatEndpoint endpoint = (HipchatEndpoint)component.createEndpoint("hipchat://localhost:8080?authToken=token&protocol=https@consumeUsers=@auser,@buser", "localhost:8080", parameters);
        assertEquals("localhost", endpoint.getConfiguration().getHost());
        assertEquals(new Integer(8080), endpoint.getConfiguration().getPort());
        assertEquals("https", endpoint.getConfiguration().getProtocol());
        assertEquals("token", endpoint.getConfiguration().getAuthToken());
        assertEquals("https://localhost:8080", endpoint.getConfiguration().hipChatUrl());
        assertEquals("/a?auth_token=token", endpoint.getConfiguration().withAuthToken("/a"));
        assertEquals(2, endpoint.getConfiguration().consumableUsers().length);
        assertTrue(Arrays.asList(endpoint.getConfiguration().consumableUsers()).contains("@auser"));
        assertTrue(Arrays.asList(endpoint.getConfiguration().consumableUsers()).contains("@buser"));
    }
}
