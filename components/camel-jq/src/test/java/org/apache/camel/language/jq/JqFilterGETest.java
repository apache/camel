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
package org.apache.camel.language.jq;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JqFilterGETest extends JqTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .filter().jq(".amount >= 1000")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testFilterMapPayload() throws Exception {
        Map b0 = Map.of("branch", "BRANCH0", "amount", 1234);
        Map b1 = Map.of("branch", "BRANCH1", "amount", 499);
        Map b2 = Map.of("branch", "BRANCH2", "amount", 4444);

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).body().isEqualTo(b0);
        getMockEndpoint("mock:result").message(1).body().isEqualTo(b2);

        template.sendBody("direct:start", b0);
        template.sendBody("direct:start", b1);
        template.sendBody("direct:start", b2);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFilterStringPayload() throws Exception {
        String b0 = "{\n"
                    + "    \"branch\": \"BRANCH0\",\n"
                    + "    \"amount\": 1234\n"
                    + "}";

        String b1 = "{\n"
                    + "    \"branch\": \"BRANCH1\",\n"
                    + "    \"amount\": 499\n"
                    + "}";

        String b2 = "{\n"
                    + "    \"branch\": \"BRANCH2\",\n"
                    + "    \"amount\": 4444\n"
                    + "}";

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).body().isEqualTo(b0);
        getMockEndpoint("mock:result").message(1).body().isEqualTo(b2);

        template.sendBody("direct:start", b0);
        template.sendBody("direct:start", b1);
        template.sendBody("direct:start", b2);

        MockEndpoint.assertIsSatisfied(context);
    }
}
