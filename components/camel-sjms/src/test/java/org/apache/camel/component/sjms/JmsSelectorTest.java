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
package org.apache.camel.component.sjms;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

public class JmsSelectorTest extends JmsTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testJmsSelector() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        String expectedBody = "Hello there!";
        String expectedBody2 = "Goodbye!";

        resultEndpoint.expectedBodiesReceived(expectedBody2);
        resultEndpoint.message(0).header("cheese").isEqualTo("y");

        template.sendBodyAndHeader("sjms:test.a", expectedBody, "cheese", "x");
        template.sendBodyAndHeader("sjms:test.a", expectedBody2, "cheese", "y");

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("sjms:test.a").to("log:test-before?showAll=true").to("sjms:test.b");
                from("sjms:test.b?messageSelector=cheese='y'").to("log:test-after?showAll=true").to("mock:result");
            }
        };
    }
}
