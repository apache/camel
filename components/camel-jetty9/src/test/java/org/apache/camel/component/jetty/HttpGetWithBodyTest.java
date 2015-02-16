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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class HttpGetWithBodyTest extends BaseJettyTest {

    @Test
    public void testGetWithBody() throws Exception {
        // use jetty http producer as it support GET with body (regular camel-http producer does not)
        Exchange result = template.request("jetty:http://localhost:{{port}}/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
                exchange.getIn().setBody("Camel");
            }
        });
        assertNotNull(result);

        assertEquals("Hello Camel", result.getOut().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp?disableStreamCache=false")
                    .convertBodyTo(String.class)
                    .to("log:foo")
                    .transform(body().prepend("Hello "));
            }
        };
    }

}
