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
package org.apache.camel.component.rss;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class RssMultipleEndpointsIssueTest extends CamelTestSupport {

    @Test
    @Disabled("A manual test")
    public void testMultipleEndpointIssueTest() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMinimumMessageCount(1);

        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("rss:http://www.iafrica.com/pls/cms/grapevine.xml?initialDelay=2000").to("mock:a");

                from("rss:http://www.iafrica.com/pls/cms/grapevine.xml?p_section=world_news&initialDelay=3000").to("mock:b");
            }
        };
    }
}
