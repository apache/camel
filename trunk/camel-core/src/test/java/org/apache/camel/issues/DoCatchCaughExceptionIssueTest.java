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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Based on user forum issue
 *
 * @version 
 */
public class DoCatchCaughExceptionIssueTest extends ContextTestSupport {

    public void testSendThatIsCaught() {
        String out = template.requestBody("direct:test", "test", String.class);
        assertEquals("Forced by me but I fixed it", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("direct:test")
                    .doTry()
                        .throwException(new IllegalArgumentException("Forced by me"))
                    .doCatch(Exception.class)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                Exception error = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                                assertEquals("Forced by me", error.getMessage());
                                exchange.getOut().setBody(error.getMessage() + " but I fixed it");
                            }
                        })
                    .end();
            }
        };
    }

}
