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

public class OnCompletionIssueTest extends ContextTestSupport {

    public void testOnCompletionIssue() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedBodiesReceived("finish", "stop", "faulted", "except");

        template.sendBody("direct:input", "finish");
        template.sendBody("direct:input", "stop");
        template.sendBody("direct:input", "fault");
        template.sendBody("direct:input", "except");

        setAssertPeriod(2000);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().parallelProcessing()
                    .log("completing ${body}")
                    .to("mock:complete");

                from("direct:input")
                    .onException(Exception.class)
                        .handled(true)
                    .end()
                    .choice()
                        .when(simple("${body} == 'stop'"))
                            .log("stopping")
                            .stop()
                        .when(simple("${body} == 'fault'"))
                            .log("faulting")
                            .setFaultBody(constant("faulted"))
                        .when(simple("${body} == 'except'"))
                            .log("excepting")
                            .throwException(new Exception("Exception requested"))
                        .end()
                        .log("finishing")
                        .to("mock:end");
            }
        };
    }
}
