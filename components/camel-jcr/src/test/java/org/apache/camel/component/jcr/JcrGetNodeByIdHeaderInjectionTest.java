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
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The JCR producer's {@code CamelJcrGetById} operation maps the properties of the retrieved node into Exchange headers.
 * A content author must not be able to use a property named after a Camel internal header (in any casing) to inject
 * that header, as those steer downstream processing (HTTP target URI, bean method dispatch, file names, and so on).
 * Ordinary document properties must still be mapped.
 */
public class JcrGetNodeByIdHeaderInjectionTest extends JcrRouteTestSupport {

    private static final String CONTENT = "content is here";

    private static final String[] CAMEL_HEADER_VARIANTS = {
            "CamelHttpUri", "camelHttpUri", "caMELHttpUri", "CAMELHTTPURI" };

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private String identifier;

    @Override
    public void doPreSetup() throws RepositoryException {
        Session session = openSession();
        Node node = session.getRootNode().addNode("injectionRoot").addNode("test");
        node.setProperty("my.contents.property", CONTENT);
        for (String variant : CAMEL_HEADER_VARIANTS) {
            node.setProperty(variant, "malicious");
        }
        identifier = node.getIdentifier();

        session.save();
        session.logout();
    }

    @Test
    public void camelHeadersInNodePropertiesAreFilteredRegardlessOfCase() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = createExchangeWithBody(identifier);
        template.send("direct:a", exchange);
        MockEndpoint.assertIsSatisfied(context);

        Message in = result.getReceivedExchanges().get(0).getIn();
        for (String variant : CAMEL_HEADER_VARIANTS) {
            // the Camel header map is case-insensitive, so this lookup also catches the other spellings
            assertNull(in.getHeader(variant),
                    "a Camel internal header must not be injectable through a JCR node property: " + variant);
        }
        assertEquals(CONTENT, in.getHeader("my.contents.property", String.class),
                "an ordinary document property must still be mapped to a header");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a")
                        .setHeader(JcrConstants.JCR_OPERATION, constant(JcrConstants.JCR_GET_BY_ID))
                        .to("jcr://user:pass@repository")
                        .to("mock:result");
            }
        };
    }
}
