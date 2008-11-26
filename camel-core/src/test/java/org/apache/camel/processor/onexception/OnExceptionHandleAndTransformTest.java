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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test inspired by end user
 */
public class OnExceptionHandleAndTransformTest extends ContextTestSupport {

    public void testOnException() throws Exception {
        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Sorry", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(0));

                // START SNIPPET: e1
                // we catch MyFunctionalException and want to mark it as handled (= no failure returned to client)
                // but we want to return a fixed text response, so we transform OUT body as Sorry.
                onException(MyFunctionalException.class)
                        .handled(true)
                        .transform().constant("Sorry");
                // END SNIPPET: e1

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new MyFunctionalException("Sorry you can not do this");
                    }
                });
            }
        };
    }
}
