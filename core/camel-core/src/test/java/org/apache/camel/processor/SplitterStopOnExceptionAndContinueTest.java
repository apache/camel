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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SplitterStopOnExceptionAndContinueTest extends ContextTestSupport {

    @Test
    public void testSplitStopOnExceptionOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World,Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitStopOnExceptionContinue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        // we do stop so we stop splitting when the exception occurs and thus we
        // only receive 1 message
        mock.expectedBodiesReceived("Hello World");
        // we continue so we get a result
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World,Kaboom,Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        // split and stop if failing during splitting
                        // enable share unit of work so the result of the splitter has the caused exception, which we can then ignore
                        // via the try .. catch
                        .split(body().tokenize(",")).shareUnitOfWork().stopOnException().process(new MyProcessor()).to("mock:split").end()
                    .endDoTry().doCatch(Exception.class)
                        // just continue
                    .end()
                    // result
                    .to("mock:result");
            }
        };
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(body)) {
                throw new IllegalArgumentException("Forced");
            }
        }
    }
}
