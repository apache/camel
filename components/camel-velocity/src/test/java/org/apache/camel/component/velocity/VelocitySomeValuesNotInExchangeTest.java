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
package org.apache.camel.component.velocity;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test with some variables missing.
 */
public class VelocitySomeValuesNotInExchangeTest extends CamelTestSupport {

    @Test
    public void testWithAllValues() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).constant("Hello Claus");
        mock.message(0).constant("You have id: 123 if an id was assigned to you.");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("name", "Claus");
        headers.put("id", 123);
        template.sendBodyAndHeaders("direct:a", "", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithSomeValues() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).constant("Hello Claus");
        mock.message(0).constant("You have id:  if an id was assigned to you.");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("name", "Claus");
        template.sendBodyAndHeaders("direct:a", "", headers);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a")
                    .to("velocity:org/apache/camel/component/velocity/someValuesNotInExchange.vm")
                    .to("mock:result");
            }
        };
    }
}