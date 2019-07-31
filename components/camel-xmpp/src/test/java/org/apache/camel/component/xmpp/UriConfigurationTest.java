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
package org.apache.camel.component.xmpp;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class UriConfigurationTest extends Assert {
    protected CamelContext context = new DefaultCamelContext();

    @Test
    public void testPrivateChatConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("xmpp://camel-user@localhost:123/test-user@localhost?password=secret&serviceName=someCoolChat");
        assertTrue("Endpoint not an XmppEndpoint: " + endpoint, endpoint instanceof XmppEndpoint);
        XmppEndpoint xmppEndpoint = (XmppEndpoint) endpoint;


        assertEquals("localhost", xmppEndpoint.getHost());
        assertEquals(123, xmppEndpoint.getPort());
        assertEquals("camel-user", xmppEndpoint.getUser());
        assertEquals("test-user@localhost", xmppEndpoint.getParticipant());
        assertEquals("secret", xmppEndpoint.getPassword());
        assertEquals("someCoolChat", xmppEndpoint.getServiceName());
    }

    @Test
    public void testGroupChatConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("xmpp://camel-user@im.google.com:123?room=cheese&password=secret&nickname=incognito");
        assertTrue("Endpoint not an XmppEndpoint: " + endpoint, endpoint instanceof XmppEndpoint);
        XmppEndpoint xmppEndpoint = (XmppEndpoint) endpoint;


        assertEquals("im.google.com", xmppEndpoint.getHost());
        assertEquals(123, xmppEndpoint.getPort());
        assertEquals("camel-user", xmppEndpoint.getUser());
        assertEquals("cheese", xmppEndpoint.getRoom());
        assertEquals("secret", xmppEndpoint.getPassword());
        assertEquals("incognito", xmppEndpoint.getNickname());
    }

    // Changes in default resource name may break
    // clients program assuming the default "Camel" resource name
    // so it is better to avoid changing it.
    @Test
    public void testDefaultResource() throws Exception {
        Endpoint endpoint = context.getEndpoint("xmpp://camel-user@im.google.com?password=secret");
        assertTrue("Endpoint not an XmppEndpoint: " + endpoint, endpoint instanceof XmppEndpoint);
        XmppEndpoint xmppEndpoint = (XmppEndpoint) endpoint;

        assertEquals("Camel", xmppEndpoint.getResource());
    }
    
    @Test
    public void testPubSubConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("xmpp://camel-user@localhost:123?password=secret&pubsub=true");
        assertTrue("Endpoint not an XmppEndpoint: " + endpoint, endpoint instanceof XmppEndpoint);
        XmppEndpoint xmppEndpoint = (XmppEndpoint) endpoint;

        assertEquals("localhost", xmppEndpoint.getHost());
        assertEquals(123, xmppEndpoint.getPort());
        assertEquals("camel-user", xmppEndpoint.getUser());
        assertEquals("secret", xmppEndpoint.getPassword());
        assertEquals(true, xmppEndpoint.isPubsub());
        assertEquals(true, xmppEndpoint.isDoc());
    }
    
}
