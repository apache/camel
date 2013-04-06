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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for useOriginalnBody option on DeadLetterChannel
 *
 * @version 
 */
public class DeadLetterChannelUseOriginalInBodyTest extends ContextTestSupport {

    public void testUseOriginalnBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:a");
        dead.expectedBodiesReceived("Hello");

        template.sendBody("direct:a", "Hello");

        assertMockEndpointsSatisfied();
    }

    public void testDoNotUseOriginalInBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:b");
        dead.expectedBodiesReceived("Hello World");

        template.sendBody("direct:b", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // will use original
                ErrorHandlerFactory a = deadLetterChannel("mock:a")
                    .maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false).useOriginalMessage();

                // will NOT use original
                ErrorHandlerFactory b = deadLetterChannel("mock:b")
                    .maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false);

                from("direct:a")
                    .errorHandler(a)
                    .setBody(body().append(" World"))
                    .process(new MyThrowProcessor());

                from("direct:b")
                    .errorHandler(b)
                    .setBody(body().append(" World"))
                    .process(new MyThrowProcessor());
            }
        };
    }

    public static class MyThrowProcessor implements Processor {

        public MyThrowProcessor() {
        }

        public void process(Exchange exchange) throws Exception {
            assertEquals("Hello World", exchange.getIn().getBody(String.class));
            throw new IllegalArgumentException("Forced");
        }
    }
}
