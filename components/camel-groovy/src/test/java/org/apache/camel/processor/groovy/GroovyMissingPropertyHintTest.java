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
package org.apache.camel.processor.groovy;

import groovy.lang.MissingPropertyException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A groovy script that uses a bean name as a variable, or an unknown variable, gets a MissingPropertyException whose
 * message says where the beans are and what the script variables are (CAMEL-24698). A field read on a body that is
 * still the payload text says to unmarshal it first (CAMEL-25330).
 */
public class GroovyMissingPropertyHintTest extends CamelTestSupport {

    @Test
    public void beanNameUsedAsVariableIsExplained() {
        context.getRegistry().bind("formatter", new StringBuilder());
        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:bean", "x"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("'formatter' is a bean in the registry, not a script variable"),
                cause.getMessage());
        assertTrue(cause.getMessage().contains("bean: {ref: formatter}"), cause.getMessage());
    }

    @Test
    public void unknownVariableListsTheScriptVariables() {
        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:unknown", "x"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("the script variables are exchange, message, body, headers"),
                cause.getMessage());
    }

    @Test
    public void fieldReadOnJsonBytesSaysToUnmarshal() {
        byte[] json = "[{\"sku\": \"A1\"}]".getBytes();
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeader("direct:field", json, "sku", "A1"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("the body is still text (a byte[])"), cause.getMessage());
        assertTrue(cause.getMessage().contains("unmarshal: json"), cause.getMessage());
    }

    @Test
    public void fieldReadOnJsonStringSaysToUnmarshal() {
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeader("direct:field", "[{\"sku\": \"A1\"}]", "sku", "A1"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("the body is still text (a String)"), cause.getMessage());
    }

    @Test
    public void fieldReadOnXmlTextSaysJacksonXml() {
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeader("direct:field", "<order><sku>A1</sku></order>", "sku", "A1"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("unmarshal: jacksonXml"), cause.getMessage());
    }

    @Test
    public void fieldReadOnCsvFileSaysCsv() {
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeaders("direct:field", "sku,qty\nA1,2".getBytes(),
                        java.util.Map.of("sku", "A1", Exchange.FILE_NAME, "orders.csv")));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("unmarshal: csv"), cause.getMessage());
    }

    @Test
    public void fieldReadOnJsonContentTypeSaysJson() {
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeaders("direct:field", "sku=A1",
                        java.util.Map.of("sku", "A1", Exchange.CONTENT_TYPE, "application/json; charset=UTF-8")));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("unmarshal: json"), cause.getMessage());
    }

    @Test
    public void fieldReadOnUnknownTextNamesTheChoices() {
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeader("direct:field", "sku=A1", "sku", "A1"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("the data format of the payload (json, jacksonXml, csv, ...)"),
                cause.getMessage());
    }

    @Test
    public void fieldReadOnParsedBodyWorks() {
        Object out = template.requestBodyAndHeader("direct:field", java.util.List.of(java.util.Map.of("sku", "A1")),
                "sku", "A1");
        assertEquals(java.util.Map.of("sku", "A1"), out);
    }

    @Test
    public void messageIsAScriptVariableAsTheHintSays() {
        String out = template.requestBodyAndHeader("direct:message", "World", "name", "Hello", String.class);
        assertEquals("Hello World", out);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bean").transform().groovy("formatter.append(body)");
                from("direct:unknown").transform().groovy("nosuch.toUpperCase()");
                from("direct:field").transform().groovy("body.find { it.sku == headers.sku }");
                from("direct:message").transform().groovy("message.getHeader('name') + ' ' + message.body");
            }
        };
    }
}
