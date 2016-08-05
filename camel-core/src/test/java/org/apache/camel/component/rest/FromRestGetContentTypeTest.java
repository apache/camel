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
package org.apache.camel.component.rest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

public class FromRestGetContentTypeTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    public void testFromRestModelContentType() throws Exception {
        Exchange out = template.request("seda:get-say-hello", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });

        assertNotNull(out);
        assertEquals("{ \"name\" : \"Donald\" }", out.getOut().getBody());
        assertEquals("application/json", out.getOut().getHeader(Exchange.CONTENT_TYPE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");

                rest("/say/hello").produces("application/json")
                    .get().to("direct:hello");

                from("direct:hello").setBody(constant("{ \"name\" : \"Donald\" }"));

            }
        };
    }
}
