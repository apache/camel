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
package org.apache.camel.component.ahc;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AhcProduce500Test extends BaseAhcTest {

    @Test
    public void testAhcProduce() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", null);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            AhcOperationFailedException cause = assertIsInstanceOf(AhcOperationFailedException.class, e.getCause());
            assertNotNull(cause);
            assertEquals(500, cause.getStatusCode());
            assertEquals("Server Error", cause.getStatusText());
            assertEquals("Does not work", cause.getResponseBody());
            assertEquals(false, cause.isRedirectError());
            assertNotNull(cause.getResponseHeaders());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to(getAhcEndpointUri())
                    .to("mock:result");

                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                                exchange.getOut().setBody("Does not work");
                            }
                        });

            }
        };
    }
}
