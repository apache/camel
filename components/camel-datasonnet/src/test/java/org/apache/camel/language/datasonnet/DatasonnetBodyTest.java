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
package org.apache.camel.language.datasonnet;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.datasonnet.document.MediaTypes;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatasonnetBodyTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:null")
                        .setBody(datasonnet("null"))
                        .setHeader("text", simple("${body}"))
                        .to("mock:null");
            }
        };
    }

    @Test
    public void testSourceWithNullBody() {
        Expression exp = context.resolveLanguage("datasonnet")
                .createExpression("body", new Object[] { String.class, "header:foo", null, null });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("foo", "Hello");

        assertEquals("Hello", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testMatchesKeepsOutputMediaType() {
        DatasonnetExpression exp = new DatasonnetExpression("body");
        exp.init(context);

        assertEquals("\"Hello\"", exp.evaluate(createJsonOutputExchange(), String.class));

        Exchange predicate = new DefaultExchange(context);
        predicate.getMessage().setBody(true);
        assertTrue(exp.matches(predicate));

        assertNull(exp.getOutputMediaType());
        assertEquals("\"Hello\"", exp.evaluate(createJsonOutputExchange(), String.class));
    }

    @Test
    public void testStreamBody() {
        Expression exp = context.resolveLanguage("datasonnet")
                .createExpression("'Hello ' + body", new Object[] { String.class, null, null, null });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new ByteArrayInputStream("World".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Hello World", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testStreamCacheBody() {
        Expression exp = context.resolveLanguage("datasonnet")
                .createExpression("'Hello ' + body", new Object[] { String.class, null, null, null });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new InputStreamCache("World".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Hello World", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testNullResult() {
        Expression exp = context.resolveLanguage("datasonnet").createExpression("null");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Hello");
        assertNull(exp.evaluate(exchange, Object.class));
    }

    @Test
    public void testNullResultInRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:null");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        template.sendBody("direct:null", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertNull(mock.getReceivedExchanges().get(0).getMessage().getHeader("text"));
    }

    @Test
    public void testNullResultPredicate() {
        DatasonnetExpression exp = new DatasonnetExpression("null");
        exp.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Hello");
        assertFalse(exp.matches(exchange));
    }

    private Exchange createJsonOutputExchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Hello");
        exchange.getMessage().setHeader(DatasonnetConstants.OUTPUT_MEDIATYPE, MediaTypes.APPLICATION_JSON_VALUE);
        return exchange;
    }
}
