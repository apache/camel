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
package org.apache.camel.component.restlet;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RestletProducerTimeoutTest extends RestletTestSupport {

    @Test
    public void testRestletProducerTimeout() throws Exception {
        try {
            template.requestBodyAndHeader("restlet:http://localhost:" + portNum + "/users/123/basic?socketTimeout=100", null, "id", 123, String.class);
            fail("Should have thrown exception");
        } catch (Exception ex) {
            // expected
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/users/{id}/basic")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Thread.sleep(2000);
                                exchange.getOut().setBody("Here is the response");
                            }
                        });
            }
        };
    }
}
