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
package org.apache.camel.component.jacksonxml;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonMarshalContentTypeHeaderTest extends CamelTestSupport {

    @Test
    public void testYes() throws Exception {
        final Map<String, Object> in = new HashMap<>();
        in.put("name", "Camel");

        Exchange out = template.request("direct:yes", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(in);
            }
        });

        assertNotNull(out);
        assertTrue(out.hasOut());
        assertEquals("application/xml", out.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    public void testYes2() throws Exception {
        final Map<String, Object> in = new HashMap<>();
        in.put("name", "Camel");

        Exchange out = template.request("direct:yes2", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(in);
            }
        });

        assertNotNull(out);
        assertTrue(out.hasOut());
        assertEquals("application/xml", out.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    public void testNo() throws Exception {
        final Map<String, Object> in = new HashMap<>();
        in.put("name", "Camel");

        Exchange out = template.request("direct:no", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(in);
            }
        });

        assertNotNull(out);
        assertTrue(out.hasOut());
        assertNull(out.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                JacksonXMLDataFormat format = new JacksonXMLDataFormat();
                from("direct:yes").marshal(format);

                from("direct:yes2").marshal().jacksonxml();

                JacksonXMLDataFormat formatNoHeader = new JacksonXMLDataFormat();
                formatNoHeader.setContentTypeHeader(false);
                from("direct:no").marshal(formatNoHeader);
            }
        };
    }

}
