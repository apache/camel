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
import javax.jcr.PropertyIterator;
import javax.jcr.Session;

import org.apache.camel.Exchange;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class JcrProducerPropertiesTest extends JcrRouteTestSupport {
    
    private static final String PROP1_NAME = "my.property.1";
    private static final String PROP1_VALUE = "my value 1";
    private static final String PROP2_NAME = "my.property.2";
    private static final String PROP2_VALUE = "my value 2";
    
    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/repository");
        super.setUp();
    }

    @Test
    public void testCreateNodeWithProperties() throws Exception {
        Session session = openSession();

        try {
            // create node
            Exchange exchange1 = ExchangeBuilder.anExchange(context)
                .withProperty(JcrConstants.JCR_NODE_NAME, "node")
                .withProperty(PROP1_NAME, PROP1_VALUE)
                .withProperty(PROP2_NAME, PROP2_VALUE)
                .build();
            Exchange out = template.send("direct:a", exchange1);
            assertNotNull(out);
            String uuid = out.getOut().getBody(String.class);

            Node node = session.getNodeByIdentifier(uuid);
            assertNotNull(node);
            assertEquals("/home/test/node", node.getPath());
            
            // Camel properties must not be persisted in the JCR repository
            PropertyIterator camelProperties = node.getProperties("Camel*");
            assertEquals(0, camelProperties.getSize());
            
            PropertyIterator properties = node.getProperties();
            assertTrue(properties.getSize() > 0);
            assertTrue(node.hasProperty(PROP1_NAME));
            assertEquals(PROP1_VALUE, node.getProperty(PROP1_NAME).getString());
            assertTrue(node.hasProperty(PROP2_NAME));
            assertEquals(PROP2_VALUE, node.getProperty(PROP2_NAME).getString());
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
