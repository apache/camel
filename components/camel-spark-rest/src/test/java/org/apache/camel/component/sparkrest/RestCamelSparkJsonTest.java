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
package org.apache.camel.component.sparkrest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RestCamelSparkJsonTest extends BaseSparkTest {

    @Test
    public void testSparkHello() throws Exception {
        Exchange out = template.request("http://localhost:" + getPort() + "/spark/hello", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            }
        });
        assertNotNull(out);

        assertEquals("application/json", out.getOut().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("{'reply': 'Hello World'}", out.getOut().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // will automatic find the spark component to use, as we setup that component in the BaseSparkTest
                rest("/spark").consumes("application/json").produces("application/json")
                    .get("/hello").to("direct:hello");

                from("direct:hello")
                        .transform().constant("{'reply': 'Hello World'}");

            }
        };
    }
}
