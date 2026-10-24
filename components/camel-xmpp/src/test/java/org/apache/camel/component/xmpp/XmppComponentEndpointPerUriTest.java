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
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Endpoints whose URIs differ only in the participant (the path) or in the user must be different endpoints: the
 * component must not hand out the endpoint of another participant or user.
 */
class XmppComponentEndpointPerUriTest {

    private final CamelContext context = new DefaultCamelContext();

    @AfterEach
    void stop() {
        context.stop();
    }

    @Test
    void endpointPerParticipant() {
        XmppEndpoint bob = context.getEndpoint("xmpp://camel-user@localhost:123/bob@localhost?password=secret",
                XmppEndpoint.class);
        XmppEndpoint carol = context.getEndpoint("xmpp://camel-user@localhost:123/carol@localhost?password=secret",
                XmppEndpoint.class);

        assertEquals("bob@localhost", bob.getParticipant());
        assertEquals("carol@localhost", carol.getParticipant());
    }

    @Test
    void endpointPerUser() {
        XmppEndpoint alice = context.getEndpoint("xmpp://alice@localhost:123/bob@localhost?password=secret",
                XmppEndpoint.class);
        XmppEndpoint dave = context.getEndpoint("xmpp://dave@localhost:123/bob@localhost?password=secret",
                XmppEndpoint.class);

        assertEquals("alice", alice.getUser());
        assertEquals("dave", dave.getUser());
    }

    @Test
    void sameUriSameEndpoint() {
        XmppEndpoint one = context.getEndpoint("xmpp://camel-user@localhost:123/bob@localhost?password=secret",
                XmppEndpoint.class);
        XmppEndpoint two = context.getEndpoint("xmpp://camel-user@localhost:123/bob@localhost?password=secret",
                XmppEndpoint.class);

        assertSame(one, two);
    }
}
