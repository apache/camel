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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class RestletProducerGet2Test extends RestletTestSupport {

    @Test
    public void testRestletProducerGet2() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("id", 123);
        headers.put("beverage.beer", "Carlsberg");

        String out = template.requestBodyAndHeaders("direct:start", null, headers, String.class);
        assertEquals("123;Donald Duck;Carlsberg", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/{id}/like/{beverage.beer}");

                from("restlet:http://localhost:" + portNum + "/users/{id}/like/{beer}")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String id = exchange.getIn().getHeader("id", String.class);
                            String beer = exchange.getIn().getHeader("beer", String.class);
                            exchange.getOut().setBody(id + ";Donald Duck;" + beer);
                        }
                    });
            }
        };
    }
}
