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
package org.apache.camel.component.jcr;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class JcrProducerTest extends JcrRouteTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/repository");
        super.setUp();
    }

    @Test
    public void testJcrProducer() throws Exception {
        Exchange exchange = createExchangeWithBody("<hello>world!</hello>");
        Exchange out = template.send("direct:a", exchange);
        assertNotNull(out);
        String uuid = out.getOut().getBody(String.class);
        Session session = openSession();
        try {
            Node node = session.getNodeByIdentifier(uuid);
            assertNotNull(node);
            assertEquals("/home/test/node", node.getPath());
            assertEquals("<hello>world!</hello>", node.getProperty("my.contents.property").getString());
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: jcr-create-node
                from("direct:a").setHeader(JcrConstants.JCR_NODE_NAME, constant("node"))
                        .setHeader("my.contents.property", body())
                        .to("jcr://user:pass@repository/home/test");
                // END SNIPPET: jcr-create-node
            }
        };
    }

}
