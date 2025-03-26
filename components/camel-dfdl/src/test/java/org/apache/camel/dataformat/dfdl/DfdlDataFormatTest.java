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
package org.apache.camel.dataformat.dfdl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dfdl.DfdlParseException;
import org.apache.camel.component.dfdl.DfdlUnparseException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.daffodil.japi.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;

import static org.junit.jupiter.api.Assertions.*;

public class DfdlDataFormatTest extends CamelTestSupport {
    @EndpointInject(value = "mock:result")
    private MockEndpoint mockEndpoint;

    @BeforeEach
    public void each() {
        mockEndpoint.reset();
    }

    @Test
    public void testParse() throws Exception {
        var template = context.createFluentProducerTemplate();
        template.to("direct:parse")
                .withBody(context.getClassResolver().loadResourceAsStream("X12-837P-message.edi.txt"))
                .send();
        mockEndpoint.expectedMessageCount(1);
        var exchange = mockEndpoint.getExchanges().get(0);
        var comp = new StreamSource(context.getClassResolver().loadResourceAsStream("X12-837P-message.xml"));
        var test = exchange.getMessage().getBody(Document.class);
        assertFalse(DiffBuilder
                .compare(comp)
                .withTest(test)
                .ignoreComments().ignoreWhitespace().build().hasDifferences());
    }

    @Test
    public void testUnparse() throws Exception {
        var template = context.createFluentProducerTemplate();
        template.to("direct:unparse")
                .withBody(context.getClassResolver().loadResourceAsStream("X12-837P-message.xml"))
                .send();
        mockEndpoint.expectedMessageCount(1);
        var exchange = mockEndpoint.getExchanges().get(0);
        var comp = new BufferedReader(
                new InputStreamReader(
                        context.getClassResolver().loadResourceAsStream("X12-837P-message.edi.txt")))
                .lines()
                .toArray(String[]::new);
        var test = exchange.getMessage().getBody(String.class).split("\\r?\\n|\\r");
        assertEquals(comp.length, test.length);
        for (int i = 0; i < test.length; i++) {
            assertEquals(comp[i], test[i], "Line " + i);
        }
    }

    @Test
    public void testParseError() throws Exception {
        var template = context.createFluentProducerTemplate();
        var exchange = template.to("direct:parse")
                .withBody(context.getClassResolver().loadResourceAsStream("X12-837P-message.xml"))
                .send();
        var exception = exchange.getException();
        assertInstanceOf(DfdlParseException.class, exception);
        var parseResult = ((DfdlParseException) exception).getParseResult();
        var location = parseResult.location();
        assertTrue(location.toString().contains("0"));
        var diagString = parseResult.getDiagnostics()
                .stream()
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining("\n"));
        assertTrue(diagString.contains("initiator 'ISA' not found"));
    }

    @Test
    public void testUnparseError() throws Exception {
        var template = context.createFluentProducerTemplate();
        var exchange = template.to("direct:unparse")
                .withBody("<Unexpected />")
                .send();
        var exception = exchange.getException();
        assertInstanceOf(DfdlUnparseException.class, exception);
        var unparseResult = ((DfdlUnparseException) exception).getUnparseResult();
        var diagString = unparseResult.getDiagnostics()
                .stream()
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining("\n"));
        assertTrue(diagString.contains("Expected element start event for {}X12_837P"));
        assertTrue(diagString.contains("{}Unexpected"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:parse")
                        .unmarshal()
                        .dfdl("X12-837P.dfdl.xsd")
                        .to("mock:result");
                from("direct:unparse")
                        .marshal()
                        .dfdl("X12-837P.dfdl.xsd")
                        .to("mock:result");
            }
        };
    }
}
