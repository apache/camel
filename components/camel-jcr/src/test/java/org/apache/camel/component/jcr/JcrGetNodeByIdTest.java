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
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class JcrGetNodeByIdTest extends JcrRouteTestSupport {
    public static final String CONTENT = "content is here";
    public static final Boolean APPROVED = true;
    private String identifier;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        Session session = openSession();
        Node node = session.getRootNode().addNode("home").addNode("test");
        node.setProperty("content.approved", APPROVED);
        node.setProperty("my.contents.property", CONTENT);
        
        
        ValueFactory valFact = session.getValueFactory();
        Value[] vals = new Value[] {valFact.createValue("value-1"), valFact.createValue("value-2")};
        node.setProperty("my.multi.valued", vals);
        
        identifier = node.getIdentifier();

        session.save();
        session.logout();
    }

    @Test
    public void testJcrProducer() throws Exception {
        result.expectedMessageCount(1);
        result.expectedHeaderReceived("my.contents.property", CONTENT);
        result.expectedHeaderReceived("content.approved", APPROVED);

        Exchange exchange = createExchangeWithBody(identifier);
        template.send("direct:a", exchange);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: jcr-get-node
                from("direct:a")
                        .setHeader(JcrConstants.JCR_OPERATION, constant(JcrConstants.JCR_GET_BY_ID))
                        .to("jcr://user:pass@repository")
                        .to("mock:result");
                // END SNIPPET: jcr-get-node
            }
        };
    }

}

