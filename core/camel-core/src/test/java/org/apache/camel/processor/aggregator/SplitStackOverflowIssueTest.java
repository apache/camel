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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;

public class SplitStackOverflowIssueTest extends ContextTestSupport {

    @Test
    public void testStackoverflow() throws Exception {
        int size = 50000;

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(size);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("Line #").append(i);
            sb.append("\n");
        }

        template.sendBody("direct:start", sb);

        MockEndpoint.assertIsSatisfied(60, SECONDS, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .split().tokenize("\n").streaming()
                        .to("log:result?groupSize=100", "mock:result");
            }
        };
    }
}
