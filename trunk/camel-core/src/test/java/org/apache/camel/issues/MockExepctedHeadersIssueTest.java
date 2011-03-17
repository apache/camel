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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class MockExepctedHeadersIssueTest extends ContextTestSupport {

    public void testHeaders() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");

        // this one does NOT add by only SET so what happens is that header1 value1 is the only tested
        resultEndpoint.expectedHeaderReceived("header2", "value2");
        resultEndpoint.expectedHeaderReceived("header1", "value1");

        template.sendBody("direct:test", null);

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testHeadersAdded() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.message(0).header("header1").isNull();
        resultEndpoint.message(0).header("header2").isEqualTo("value2");

        template.sendBody("direct:test", null);

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test")
                    .setHeader("header2", constant("value2"))
                    .to("mock:result");
            }
        };
    }
}
