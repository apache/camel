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
package org.apache.camel.language.groovy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A script that builds text with a GString ("Hello ${name}") gives a String. A GString is not a String: it is not equal
 * to a String, and components that check for a String do not take it as text (camel-jms sent it as an ObjectMessage).
 */
public class GroovyGStringResultTest extends CamelTestSupport {

    @Test
    public void testSetBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:body");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).body().isInstanceOf(String.class);

        template.sendBody("direct:body", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetHeaderAndVariable() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:header");
        mock.expectedHeaderReceived("greeting", "Hello World");
        mock.message(0).header("greeting").isInstanceOf(String.class);
        mock.expectedVariableReceived("greeting", "Bye World");
        mock.message(0).variable("greeting").isInstanceOf(String.class);

        template.sendBody("direct:header", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testScriptReturnsGStringInLoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:stock");
        mock.expectedBodiesReceived("not enough CAMEL-CAP", "reserved");
        mock.allMessages().body().isInstanceOf(String.class);

        template.sendBody("direct:stock", Map.of("lines", List.of(Map.of("sku", "CAMEL-MUG"), Map.of("sku", "CAMEL-CAP"))));
        template.sendBody("direct:stock", Map.of("lines", List.of(Map.of("sku", "CAMEL-MUG"))));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPredicate() {
        // a GString is a predicate as a String is: empty text and the text false do not match
        Predicate predicate = context.resolveLanguage("groovy").createPredicate("\"${body}\"");
        assertTrue(predicate.matches(exchangeWithBody("true")));
        assertTrue(predicate.matches(exchangeWithBody("Hello")));
        assertFalse(predicate.matches(exchangeWithBody("false")));
        assertFalse(predicate.matches(exchangeWithBody("")));
    }

    @Test
    public void testScriptingLanguage() {
        ScriptingLanguage language = (ScriptingLanguage) context.resolveLanguage("groovy");
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("name", "World");
        Object value = language.evaluate("\"Hello ${name}\"", bindings, Object.class);
        assertInstanceOf(String.class, value);
        assertEquals("Hello World", value);
    }

    private Exchange exchangeWithBody(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:body")
                        .setBody().groovy("\"Hello ${body}\"")
                        .to("mock:body");

                from("direct:header")
                        .setHeader("greeting").groovy("\"Hello ${body}\"")
                        .setVariable("greeting").groovy("\"Bye $body\"")
                        .to("mock:header");

                from("direct:stock")
                        .transform().groovy("""
                                def stock = ['CAMEL-MUG': 42, 'CAMEL-CAP': 0]
                                for (line in body.lines) {
                                    def sku = line.sku
                                    if (!stock[sku]) {
                                        return "not enough ${sku}"
                                    }
                                }
                                return "reserved"
                                """)
                        .to("mock:stock");
            }
        };
    }
}
