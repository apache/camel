/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import static org.apache.camel.component.restlet.RestletTestSupport.portNum;
import org.junit.Test;

public class RestletProducerTimeoutTest extends RestletTestSupport {

    @Test
    public void testRestletProducerGet() throws Exception {
        String out = template.requestBodyAndHeader("direct:start", null, "id", 123, String.class);
        assertEquals(null, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/123/basic?timeout=500").to("log:reply");

                from("restlet:http://localhost:" + portNum + "/users/{id}/basic")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Thread.sleep(1000);
                            }
                        });
            }
        };
    }
}
