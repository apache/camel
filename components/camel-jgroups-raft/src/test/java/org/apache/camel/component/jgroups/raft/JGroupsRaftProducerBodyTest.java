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
package org.apache.camel.component.jgroups.raft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body that does not convert to bytes fails the exchange (no leader is needed, as nothing is set in the log).
 */
public class JGroupsRaftProducerBodyTest extends JGroupsRaftAbstractTest {
    private static final String ENDPOINT_URI
            = "jgroups-raft:JGroupsRaftProducerBodyTest?raftId=A&channelProperties=raftABC.xml";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(ENDPOINT_URI);
            }
        };
    }

    @Test
    public void shouldFailMapBody() {
        Exchange res = template.send("direct:start", e -> e.getIn().setBody(Map.of("key", "value")));

        assertInvalidPayload(res);
    }

    @Test
    public void shouldFailIntegerBody() {
        Exchange res = template.send("direct:start", e -> e.getIn().setBody(123));

        assertInvalidPayload(res);
    }

    @Test
    public void shouldFailPojoBody() {
        Exchange res = template.send("direct:start", e -> e.getIn().setBody(new Object()));

        assertInvalidPayload(res);
    }

    @Test
    public void shouldFailListBody() {
        Exchange res = template.send("direct:start", e -> e.getIn().setBody(new ArrayList<>(List.of("a", "b"))));

        assertInvalidPayload(res);
        assertTrue(res.getException().getMessage().contains("java.util.ArrayList"), res.getException().getMessage());
    }

    @Test
    public void shouldSkipNullBody() {
        Exchange res = template.send("direct:start", e -> e.getIn().setBody(null));

        assertNull(res.getException());
        assertNull(res.getIn().getBody());
    }

    private static void assertInvalidPayload(Exchange res) {
        assertNotNull(res.getException(), "the exchange should fail");
        assertInstanceOf(InvalidPayloadException.class, res.getException(), res.getException().toString());
    }
}
