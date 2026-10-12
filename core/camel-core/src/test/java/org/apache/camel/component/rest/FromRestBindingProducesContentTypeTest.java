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
package org.apache.camel.component.rest;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FromRestBindingProducesContentTypeTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        // stands in for camel-jackson, marking what it marshals
        jndi.bind("jackson", (DataFormatFactory) MarkingDataFormat::new);
        return jndi;
    }

    @Test
    public void testBinaryProduces() {
        assertResponse("seda:get-binary", null, "application/octet-stream", "data");
    }

    @Test
    public void testPlainProducesWithJsonAccept() {
        assertResponse("seda:get-plain", "application/json", "text/plain", "data");
    }

    @Test
    public void testWildcardProducesFallsBackToJson() {
        assertResponse("seda:get-wildcard", null, "application/json", "json:data");
    }

    @Test
    public void testMultiValueProducesPicksJson() {
        assertResponse("seda:get-multi", null, "application/json", "json:data");
    }

    @Test
    public void testNoProducesFallsBackToJson() {
        assertResponse("seda:get-none", null, "application/json", "json:data");
    }

    private void assertResponse(String uri, String accept, String contentType, String body) {
        Exchange out = template.request(uri, exchange -> exchange.getIn().setHeader("Accept", accept));

        assertEquals(contentType, out.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals(body, out.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").bindingMode(RestBindingMode.json);

                rest("/binary").produces("application/octet-stream").get().to("direct:binary");
                rest("/plain").produces("text/plain").get().to("direct:plain");
                rest("/wildcard").produces("*/*").get().to("direct:wildcard");
                rest("/multi").produces("text/plain,application/json").get().to("direct:multi");
                rest("/none").get().to("direct:none");

                from("direct:binary").setBody(constant("data"));
                from("direct:plain").setBody(constant("data"));
                from("direct:wildcard").setBody(constant("data"));
                from("direct:multi").setBody(constant("data"));
                from("direct:none").setBody(constant("data"));
            }
        };
    }

    private static class MarkingDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            stream.write(("json:" + graph).getBytes());
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) {
            return stream;
        }
    }
}
