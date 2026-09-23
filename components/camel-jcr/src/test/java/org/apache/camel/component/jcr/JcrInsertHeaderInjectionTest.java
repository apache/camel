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
package org.apache.camel.component.jcr;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JCR producer's insert operation persists message headers as node properties. A Camel internal header carried on
 * the message (in any casing) must not be persisted as a node property, as those are operational headers rather than
 * document content; ordinary headers must still be stored.
 */
public class JcrInsertHeaderInjectionTest extends JcrRouteTestSupport {

    private static final String CONTENT = "content is here";

    private static final String[] CAMEL_HEADER_VARIANTS = {
            "CamelHttpUri", "camelHttpUri", "caMELHttpUri", "CAMELHTTPURI" };

    @Test
    public void camelHeadersAreNotPersistedAsNodeProperties() throws Exception {
        Exchange exchange = createExchangeWithBody("");
        exchange.getIn().setHeader(JcrConstants.JCR_NODE_NAME, "node");
        exchange.getIn().setHeader("my.contents.property", CONTENT);
        for (String variant : CAMEL_HEADER_VARIANTS) {
            exchange.getIn().setHeader(variant, "malicious");
        }

        Exchange out = template.send("direct:a", exchange);
        String identifier = out.getMessage().getBody(String.class);

        Session session = openSession();
        try {
            Node node = session.getNodeByIdentifier(identifier);
            assertTrue(node.hasProperty("my.contents.property"),
                    "an ordinary header must still be stored as a node property");
            assertEquals(CONTENT, node.getProperty("my.contents.property").getString());
            for (String variant : CAMEL_HEADER_VARIANTS) {
                assertFalse(node.hasProperty(variant),
                        "a Camel internal header must not be persisted as a node property: " + variant);
            }
        } finally {
            session.logout();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").to("jcr://user:pass@repository/home/test");
            }
        };
    }
}
