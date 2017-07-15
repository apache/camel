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
package org.apache.camel.language;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;

/**
 *
 */
public class TokenXMLPairMultiNamespaceSplitTest extends TokenXMLPairNamespaceSplitTest {

    public void testTokenXMLPair() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(3);
        mock.message(0).body().isEqualTo("<order id=\"1\" xmlns=\"http:acme.com\" xmlns:foo=\"http:foo.com\">Camel in Action</order>");
        mock.message(1).body().isEqualTo("<order id=\"2\" xmlns=\"http:acme.com\" xmlns:foo=\"http:foo.com\">ActiveMQ in Action</order>");
        mock.message(2).body().isEqualTo("<order id=\"3\" xmlns=\"http:acme.com\" xmlns:foo=\"http:foo.com\">DSL in Action</order>");

        String body = createBody();
        template.sendBodyAndHeader("file:target/pair", body, Exchange.FILE_NAME, "orders.xml");

        assertMockEndpointsSatisfied();
    }

    @Ignore
    public void testTokenXMLPair2() throws Exception {
        // noop
    }

    @Override
    protected String createBody() {
        // multiple namespaces on parent on the same line
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n");
        sb.append("<orders xmlns=\"http:acme.com\" xmlns:foo=\"http:foo.com\">\n");
        sb.append("  <order id=\"1\">Camel in Action</order>\n");
        sb.append("  <order id=\"2\">ActiveMQ in Action</order>\n");
        sb.append("  <order id=\"3\">DSL in Action</order>\n");
        sb.append("</orders>");
        return sb.toString();
    }

}