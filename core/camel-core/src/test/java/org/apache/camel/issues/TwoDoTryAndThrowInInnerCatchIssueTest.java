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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.TryDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Based on user forum issue
 */
public class TwoDoTryAndThrowInInnerCatchIssueTest extends ContextTestSupport {

    @Test
    public void testSendThatIsCaught() throws Exception {
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        String xml = ecc.getModelToXMLDumper().dumpModelAsXml(context, context.getRouteDefinition("myroute"));
        log.info(xml);

        try {
            template.requestBody("direct:test", "test", String.class);
        } catch (Exception e) {
            fail("Should not fail");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                TryDefinition try1 = from("direct:test").routeId("myroute").doTry();

                TryDefinition try2 = try1.doTry();

                try2.throwException(new IllegalArgumentException("Forced by me"))
                        .doCatch(Exception.class)
                        .log(LoggingLevel.INFO, TwoDoTryAndThrowInInnerCatchIssueTest.class.getName(), "docatch 1")
                        .throwException(new IllegalArgumentException("Second forced by me"))
                        .end();

                try1.doCatch(Exception.class)
                        .log(LoggingLevel.INFO, TwoDoTryAndThrowInInnerCatchIssueTest.class.getName(), "docatch 3")
                        .end();

                // stacked doTry in Java DSL has a flaw so you can do as above
                /*
                from("direct:test").
                    doTry().
                        doTry().
                            throwException(new IllegalArgumentException("Forced by me"))
                        .doCatch(Exception.class)
                            .log(LoggingLevel.INFO,DoThrowInCatchIssueTest.class.getName(), "docatch 1")
                            .throwException(new IllegalArgumentException("Second forced by me"))
                        .endDoTry() // end catch block
                    .endDoTry() // end inner try
                    .doCatch(Exception.class)
                        .log(LoggingLevel.INFO,DoThrowInCatchIssueTest.class.getName(), "docatch 3")
                    .end();
                            }
                        };*/
            }
        };
    }
}
