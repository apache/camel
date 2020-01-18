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
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JcrProducerSubNodeTest extends JcrRouteTestSupport {

    @Test
    public void testCreateNodeAndSubNode() throws Exception {
        Session session = openSession();

        try {
            // create node
            Exchange exchange1 = ExchangeBuilder.anExchange(context)
                .withHeader(JcrConstants.JCR_NODE_NAME, "node")
                .build();
            Exchange out1 = template.send("direct:a", exchange1);
            assertNotNull(out1);
            String uuidNode = out1.getMessage().getBody(String.class);

            Node node = session.getNodeByIdentifier(uuidNode);
            assertNotNull(node);
            assertEquals("/home/test/node", node.getPath());
            
            // create sub node
            Exchange exchange2 = ExchangeBuilder.anExchange(context)
                .withHeader(JcrConstants.JCR_NODE_NAME, "node/subnode")
                .build();
            Exchange out2 = template.send("direct:a", exchange2);
            assertNotNull(out2);
            String uuidSubNode = out2.getMessage().getBody(String.class);
            
            Node subNode = session.getNodeByIdentifier(uuidSubNode);
            assertNotNull(subNode);
            assertEquals("/home/test/node/subnode", subNode.getPath());
            assertNotNull(subNode.getParent());
            assertEquals("/home/test/node", subNode.getParent().getPath());
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
                from("direct:a")
                    .to("jcr://user:pass@repository/home/test");
            }
        };
    }
}
