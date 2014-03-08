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
package org.apache.camel.component.krati;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KratiProducerTest extends CamelTestSupport {

    @Test
    public void testPut() throws Exception {
        MockEndpoint endpoint = context.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMessageCount(3);

        template.sendBodyAndHeader("direct:put", new ValueObject("TEST1"), KratiConstants.KEY, new KeyObject("1"));
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST2"), KratiConstants.KEY, new KeyObject("2"));
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST3"), KratiConstants.KEY, new KeyObject("3"));

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testPutAndGet() throws Exception {
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST1"), KratiConstants.KEY, new KeyObject("1"));
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST2"), KratiConstants.KEY, new KeyObject("2"));
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST3"), KratiConstants.KEY, new KeyObject("3"));

        Object result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, new KeyObject("3"));
        assertEquals(new ValueObject("TEST3"), result);
    }

    @Test
    public void testPutAndGetPreserveHeaders() throws Exception {
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST1"), KratiConstants.KEY, new KeyObject("1"));
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST2"), KratiConstants.KEY, new KeyObject("2"));
        template.sendBodyAndHeader("direct:put", new ValueObject("TEST3"), KratiConstants.KEY, new KeyObject("3"));

        Exchange out = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("foo", 123);
                exchange.getIn().setHeader(KratiConstants.KEY, new KeyObject("3"));
            }
        });
        assertNotNull(out);
        assertEquals(123, out.getOut().getHeader("foo"));
        assertEquals(new ValueObject("TEST3"), out.getOut().getBody());
    }

    @Test
    public void testPutDeleteAndGet() throws Exception {
        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "4");
        template.requestBodyAndHeader("direct:delete", null, KratiConstants.KEY, "4");

        Object result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "4");
        assertEquals(null, result);
    }

    @Test
    public void testPutDeleteAllAndGet() throws Exception {
        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "3");
        template.requestBodyAndHeader("direct:deleteall", null, KratiConstants.KEY, "3");

        Object result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "1");
        assertEquals(null, result);
        result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "2");
        assertEquals(null, result);
        result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "3");
        assertEquals(null, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:put")
                        .to("krati:target/test/producertest")
                        .to("mock:results");

                from("direct:get").setHeader(KratiConstants.KRATI_OPERATION, constant(KratiConstants.KRATI_OPERATION_GET))
                        .to("krati:target/test/producertest");

                from("direct:delete").setHeader(KratiConstants.KRATI_OPERATION, constant(KratiConstants.KRATI_OPERATION_DELETE))
                        .to("krati:target/test/producertest");

                from("direct:deleteall").setHeader(KratiConstants.KRATI_OPERATION, constant(KratiConstants.KRATI_OPERATION_DELETEALL))
                        .to("krati:target/test/producertest");
            }
        };
    }
}
