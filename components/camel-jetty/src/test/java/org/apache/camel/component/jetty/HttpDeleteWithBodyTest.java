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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class HttpDeleteWithBodyTest extends BaseJettyTest {

    @Test
    public void testHttpDeleteWithBodyFalseTest() throws Exception {
        byte[] data = "World".getBytes();
        String out = template.requestBodyAndHeader("http://localhost:{{port}}/test", data, Exchange.HTTP_METHOD, "DELETE", String.class);
        assertEquals("Bye ", out);
    }

    @Test
    public void testHttpDeleteWithBodyTrueTest() throws Exception {
        byte[] data = "World".getBytes();
        String out = template.requestBodyAndHeader("http://localhost:{{port}}/test?deleteWithBody=true", data, Exchange.HTTP_METHOD, "DELETE", String.class);
        assertEquals("Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://http://localhost:{{port}}/test").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
                        assertEquals("DELETE", method);
                    }
                }).transform(simple("Bye ${body}"));
            }
        };
    }

}
