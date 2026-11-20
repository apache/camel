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
package org.apache.camel.processor.transformer;

import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultTransformerRegistry;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class UnchangedDataTypeTransformerTest extends ContextTestSupport {

    private final UnchangedDataTypeTransformer transformer = new UnchangedDataTypeTransformer();

    @Test
    public void shouldLeaveTheMessageAsItIs() throws Exception {
        Map<String, Object> body = Map.of("status", "ok");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader("file", "test.txt");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertSame(body, exchange.getMessage().getBody());
        assertEquals("test.txt", exchange.getMessage().getHeader("file"));
        assertNull(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals(1, exchange.getMessage().getHeaders().size());
    }

    @Test
    public void shouldKeepAnExistingContentType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("{\"status\":\"ok\"}");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertEquals("application/json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    public void shouldResolveTransformer() {
        DefaultTransformerRegistry transformerRegistry = new DefaultTransformerRegistry(context);
        for (String name : List.of("application-x-unchanged", "camel:application-x-unchanged")) {
            Transformer resolved = transformerRegistry.resolveTransformer(new TransformerKey(name));
            assertNotNull(resolved, name);
            assertInstanceOf(UnchangedDataTypeTransformer.class, resolved, name);
        }
    }

    @Test
    public void theBodyTypesSayTheBody() {
        // CAMEL-25928: any body in, and the body as it was out
        BodyTypes bodyTypes = UnchangedDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(List.of(Carrier.ANY), List.of(bodyTypes.transformerIn().value()));
        assertEquals(List.of(Carrier.UNCHANGED), List.of(bodyTypes.transformerOut().value()));
    }

    @Test
    public void shouldLeaveTheBodyInARoute() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(42);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(Integer.class);
        getMockEndpoint("mock:result").message(0).header(Exchange.CONTENT_TYPE).isNull();

        template.sendBody("direct:start", 42);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transformDataType("application-x-unchanged")
                        .to("mock:result");
            }
        };
    }
}
