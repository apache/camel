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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class MulticastParallelFailureEndpointTest extends ContextTestSupport {

    public void testMulticastParallel() throws Exception {
        Exchange result = runTest("direct:run");
        assertNotNull(result);
        assertEquals("direct://a", result.getProperty(Exchange.FAILURE_ENDPOINT));
    }

    public void testMulticastParallelWithTryCatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        Exchange result = runTest("direct:start");
        
        // try..catch block should clear handled exceptions
        assertNotNull(result);
        assertEquals(null, result.getProperty(Exchange.FAILURE_ENDPOINT));
    }

    public Exchange runTest(String uri) throws Exception {
        MockEndpoint mr = getMockEndpoint("mock:run");
        MockEndpoint ma = getMockEndpoint("mock:a");
        MockEndpoint mb = getMockEndpoint("mock:b");
        mr.expectedMessageCount(0);
        ma.expectedMessageCount(0);
        mb.expectedMessageCount(1);

        Exchange request = new DefaultExchange(context, ExchangePattern.InOut);
        request.getIn().setBody("Hello World");
        Exchange result = template.send(uri, request);

        assertMockEndpointsSatisfied();
        return result;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("direct:run")
                    .doCatch(IllegalArgumentException.class)
                        // ignore
                    .end()
                    .to("mock:result");

                from("direct:run")
                    .multicast().parallelProcessing()
                        .to("direct:a", "direct:b")
                    .end()
                    .to("mock:run");

                from("direct:a").throwException(new IllegalArgumentException("Oops...")).to("mock:a");
                from("direct:b").setBody(constant("Bye World")).to("mock:b");
            }
        };
    }
}
