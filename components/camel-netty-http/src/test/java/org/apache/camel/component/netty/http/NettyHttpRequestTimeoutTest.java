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
package org.apache.camel.component.netty.http;

import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyHttpRequestTimeoutTest extends BaseNettyTestSupport {

    @Test
    public void testRequestTimeout() {
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/timeout?requestTimeout=1000", "Hello Camel",
                    String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
            assertNotNull(cause);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost:{{port}}/timeout")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);

                                if (body.contains("Camel")) {
                                    Thread.sleep(3000);
                                }
                            }
                        })
                        .transform().constant("Bye World");

            }
        };
    }
}
