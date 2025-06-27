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

public class MockExpectedHeaderCustomTest extends ContextTestSupport {

    @Test
    public void testHeaderCustom() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        // the header(num) becomes input to the custom expression
        mock.message(0).header("num").expression(o -> {
            int num = (int) o;
            return num * 2;
        }).isLessThan(10);

        // the header(num) becomes input to the simple language as "body"
        mock.message(1).header("num").expression(o -> {
            int num = (int) o;
            return num * 3;
        }).isGreaterThan(10);

        template.sendBodyAndHeader("direct:test", "message 1", "num", 3);
        template.sendBodyAndHeader("direct:test", "message 2", "num", 7);

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
