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
package org.apache.camel.language.jxpath;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class JXPathFilterTest extends CamelTestSupport {

    @Test
    public void testSendMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        sendBody("direct:start", new PersonBean("James", "London"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        sendBody("direct:start", new PersonBean("Hiram", "Tampa"));

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testSendNullMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        sendBody("direct:start", new PersonBean(null, "Tampa"));

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").
                        filter().jxpath("in/body/name='James'").
                        to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
