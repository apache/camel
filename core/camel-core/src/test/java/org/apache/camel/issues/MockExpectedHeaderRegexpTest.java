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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class MockExpectedHeaderRegexpTest extends ContextTestSupport {

    @Test
    public void testHeaderRegexp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        // should match
        mock.message(1).header("cheese").regex("value[2,3]");
        mock.message(2).header("cheese").regex("value[2,3]");
        // should not match
        mock.message(0).header("cheese").not().regex("value[2,3]");
        mock.message(3).header("cheese").not().regex("value[2,3]");

        template.sendBodyAndHeader("direct:test", "message 1", "cheese", "value1");
        template.sendBodyAndHeader("direct:test", "message 2", "cheese", "value2");
        template.sendBodyAndHeader("direct:test", "message 3", "cheese", "value3");
        template.sendBodyAndHeader("direct:test", "message 4", "cheese", "value4");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").to("mock:result");
            }
        };
    }
}
