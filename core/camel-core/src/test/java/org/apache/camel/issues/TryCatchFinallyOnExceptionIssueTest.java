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
import org.junit.Test;

public class TryCatchFinallyOnExceptionIssueTest extends ContextTestSupport {

    @Test
    public void testTryCatchFinallyOnExceptionIssue() throws Exception {
        // doTry .. doCatch .. doFinally uses its own error handling so we wont
        // get one here
        getMockEndpoint("mock:error").expectedMessageCount(0);

        getMockEndpoint("mock:finally").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").onException(Exception.class).handled(true).redeliveryDelay(0).maximumRedeliveries(2).to("mock:error").end().doTry()
                    .throwException(new IllegalArgumentException("Damn")).doFinally().to("mock:finally").end().to("mock:end");
            }
        };
    }
}
