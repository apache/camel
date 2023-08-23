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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OnCompletionIssueTest extends ContextTestSupport {

    @Test
    public void testOnCompletionIssue() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedBodiesReceivedInAnyOrder("finish", "stop", "ile", "markRollback");

        MockEndpoint failed = getMockEndpoint("mock:failed");
        failed.expectedBodiesReceivedInAnyOrder("npe", "rollback");

        template.sendBody("direct:input", "finish");
        template.sendBody("direct:input", "stop");
        template.sendBody("direct:input", "ile");
        template.sendBody("direct:input", "markRollback");

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:input", "npe"),
                "Should have thrown exception");

        assertEquals("Darn NPE", e.getCause().getMessage());

        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:input", "rollback"),
                "Should have thrown exception");

        assertIsInstanceOf(RollbackExchangeException.class, ex.getCause());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().onFailureOnly().parallelProcessing().log("failing ${body}").to("mock:failed");

                onCompletion().onCompleteOnly().parallelProcessing().log("completing ${body}").to("mock:complete");

                from("direct:input").onException(IllegalArgumentException.class).handled(true).end().choice()
                        .when(simple("${body} == 'stop'")).log("stopping").stop()
                        .when(simple("${body} == 'ile'")).log("excepting")
                        .throwException(new IllegalArgumentException("Exception requested")).when(simple("${body} == 'npe'"))
                        .log("excepting").throwException(new NullPointerException("Darn NPE"))
                        .when(simple("${body} == 'rollback'")).log("rollback").rollback()
                        .when(simple("${body} == 'markRollback'")).log("markRollback").markRollbackOnly().end().log("finishing")
                        .to("mock:end");
            }
        };
    }
}
