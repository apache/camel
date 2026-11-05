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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import groovy.lang.MissingMethodException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * new JsonSlurper().parseText(body) on a body of bytes gets a MissingMethodException whose first line says that
 * parseText takes text, and how to read the bytes (r26 of the local-model benchmark: a JMS message read as JSON).
 */
public class GroovyMissingMethodHintTest extends CamelTestSupport {

    private static final byte[] ORDER = "{\"orderId\": \"ORD-1001\", \"lines\": [{\"sku\": \"A1\"}]}"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    public void parseTextOfBytesSaysToParseTheBytes() {
        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:body", ORDER));
        MissingMethodException cause = assertInstanceOf(MissingMethodException.class, e.getCause());
        // the hint is on the first line, which is the line a log shows
        String first = cause.getMessage().lines().findFirst().orElseThrow();
        assertTrue(first.startsWith("No signature of method: parseText for class: groovy.json.JsonSlurper"), first);
        assertTrue(first.contains("(the body is bytes (a byte[])"), first);
        assertTrue(first.contains("parseText takes text, so parse the bytes with new groovy.json.JsonSlurper().parse(body),"
                                  + " or unmarshal it first (unmarshal: json"),
                first);
    }

    @Test
    public void parseTextOfBytesInAHeaderSaysToParseTheBytes() {
        Exception e = assertThrows(Exception.class,
                () -> template.sendBodyAndHeader("direct:header", "x", "stock", ORDER));
        MissingMethodException cause = assertInstanceOf(MissingMethodException.class, e.getCause());
        assertTrue(cause.getMessage().contains("(the value is bytes (a byte[]), and parseText takes text: parse the bytes"
                                               + " with new groovy.json.JsonSlurper().parse(...))"),
                cause.getMessage());
        assertFalse(cause.getMessage().contains("the body is"), cause.getMessage());
    }

    @Test
    public void anotherMissingMethodHasNoHint() {
        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:other", "x"));
        MissingMethodException cause = assertInstanceOf(MissingMethodException.class, e.getCause());
        assertFalse(cause.getMessage().contains("parseText takes text"), cause.getMessage());
    }

    @Test
    public void parseOfBytesWorksAsTheHintSays() {
        Object out = template.requestBody("direct:parse", ORDER);
        assertEquals(List.of(Map.of("sku", "A1")), out);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:body").transform().groovy("new groovy.json.JsonSlurper().parseText(body).lines");
                from("direct:header").transform().groovy("new groovy.json.JsonSlurper().parseText(headers.stock)");
                from("direct:other").transform().groovy("body.noSuchMethod(1, 2)");
                from("direct:parse").transform().groovy("new groovy.json.JsonSlurper().parse(body).lines");
            }
        };
    }
}
