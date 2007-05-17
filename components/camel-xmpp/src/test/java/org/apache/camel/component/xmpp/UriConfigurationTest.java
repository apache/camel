/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.xmpp;

import junit.framework.TestCase;
import junit.framework.Assert;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision$
 */
public class UriConfigurationTest extends TestCase {
    protected CamelContext context = new DefaultCamelContext();

    public void testPrivateChatConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("xmpp://camel-user@localhost:123/test-user@localhost");
        assertTrue("Endpoint not an XmppEndpoint: " + endpoint, endpoint instanceof XmppEndpoint);
        XmppEndpoint xmppEndpoint = (XmppEndpoint) endpoint;


        assertEquals("localhost", xmppEndpoint.getHost());
        assertEquals(123, xmppEndpoint.getPort());
        assertEquals("camel-user", xmppEndpoint.getUser());
        assertEquals("test-user@localhost", xmppEndpoint.getParticipant());
    }

    public void testGroupChatConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("xmpp://camel-user@im.google.com:123?room=cheese");
        assertTrue("Endpoint not an XmppEndpoint: " + endpoint, endpoint instanceof XmppEndpoint);
        XmppEndpoint xmppEndpoint = (XmppEndpoint) endpoint;


        assertEquals("im.google.com", xmppEndpoint.getHost());
        assertEquals(123, xmppEndpoint.getPort());
        assertEquals("camel-user", xmppEndpoint.getUser());
        assertEquals("cheese", xmppEndpoint.getRoom());
    }
}
