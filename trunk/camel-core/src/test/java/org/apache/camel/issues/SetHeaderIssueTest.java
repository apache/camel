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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class SetHeaderIssueTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;
    protected String matchingBody = "<person xmlns='urn:cheese' name='James' city='London'/>";
    protected String notMatchingBody = "<person xmlns='urn:cheese' name='Hiram' city='Tampa'/>";

    public void testSendMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(1);

        sendBody("direct:start", matchingBody);

        resultEndpoint.assertIsSatisfied();
    }

    public void testSendNotMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        sendBody("direct:start", notMatchingBody);

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                Namespaces ns = new Namespaces("foo", "urn:cheese");

                from("direct:start").
                        unmarshal().string().
                        setHeader("foo", ns.xpath("/foo:person[@name='James']")).
                        filter(ns.xpath("/foo:person[@name='James']")).
                        to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
