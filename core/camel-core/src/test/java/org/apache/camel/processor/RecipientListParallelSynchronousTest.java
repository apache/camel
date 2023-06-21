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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecipientListParallelSynchronousTest extends ContextTestSupport {

    private String before;
    private String middle;
    private String middle2;
    private String after;

    @Test
    public void testSynchronous() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:a", "A", "whereTo", "direct:b,direct:c");

        assertMockEndpointsSatisfied();

        Assertions.assertNotEquals(before, middle);
        Assertions.assertNotEquals(before, middle2);
        Assertions.assertNotEquals(after, middle);
        Assertions.assertNotEquals(after, middle2);
        Assertions.assertEquals(before, after);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                        .process(e -> {
                            before = Thread.currentThread().getName();
                        })
                        .recipientList(header("whereTo")).parallelProcessing().synchronous()
                        .process(e -> {
                            after = Thread.currentThread().getName();
                        })
                        .to("mock:end");

                from("direct:b")
                        .process(e -> {
                            middle = Thread.currentThread().getName();
                        });

                from("direct:c")
                        .process(e -> {
                            middle2 = Thread.currentThread().getName();
                        });

            }
        };
    }

}
