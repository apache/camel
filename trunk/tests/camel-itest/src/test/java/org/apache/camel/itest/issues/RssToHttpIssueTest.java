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
package org.apache.camel.itest.issues;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class RssToHttpIssueTest extends CamelTestSupport {

    @Test
    @Ignore
    public void testRssToHttpIssueTest() throws Exception {
        // ignore as it requires to be online for testing
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Namespaces ns = new Namespaces("atom", "http://www.w3.org/2005/Atom");
                from("rss:http://www.plosone.org/article/feed")
                        .marshal().rss()
                        .setHeader(Exchange.HTTP_URI).xpath("//atom:entry/atom:link[@type=\"application/pdf\"]/@href", ns)
                        .to("http://dummy")
                        .to("mock:end");
            }
        };
    }
}
