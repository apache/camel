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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dfdl.DfdlParseException;
import org.apache.camel.component.dfdl.DfdlUnparseException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.daffodil.japi.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;

import static org.junit.jupiter.api.Assertions.*;

public class DfdlDataFormatTest extends CamelTestSupport {
    @EndpointInject(value = "mock:result")
    private MockEndpoint mockEndpoint;

    @EndpointInject(value = "mock:error")
    private MockEndpoint errorEndpoint;

    @BeforeEach
    public void each() {
        mockEndpoint.reset();
        errorEndpoint.reset();
    }

    @Test
    public void testParse() throws Exception {
        mockEndpoint.expectedMessageCount(1);
        var template = context.createFluentProducerTemplate();
        template.to("direct:parse")
                .withBody(context.getClassResolver().loadResourceAsStream("X12-837P-message.edi.txt"))
                .send();
        MockEndpoint.assertIsSatisfied(context);
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
        mockEndpoint.expectedMessageCount(1);
        var template = context.createFluentProducerTemplate();
        template.to("direct:unparse")
                .withBody(context.getClassResolver().loadResourceAsStream("X12-837P-message.xml"))
                .send();
        MockEndpoint.assertIsSatisfied(context);
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
        assertEquals(0, mockEndpoint.getReceivedCounter());
        var parseResult = ((DfdlParseException) exception).getParseResult();
        var location = parseResult.location();
        assertTrue(location.toString().contains("0"));
        var diagString = parseResult.getDiagnostics()
                .stream()
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining("\n"));
        assertTrue(diagString.contains("initiator 'ISA' not found"));
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().startsWith("DFDL parse failed: "), exception.getMessage());
        assertTrue(exception.getMessage().contains("initiator 'ISA' not found"), exception.getMessage());
    }

    @Test
    public void testUnparseError() throws Exception {
        var template = context.createFluentProducerTemplate();
        var exchange = template.to("direct:unparse")
                .withBody("<Unexpected />")
                .send();
        var exception = exchange.getException();
        assertInstanceOf(DfdlUnparseException.class, exception);
        assertEquals(0, mockEndpoint.getReceivedCounter());
        var unparseResult = ((DfdlUnparseException) exception).getUnparseResult();
        var diagString = unparseResult.getDiagnostics()
                .stream()
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining("\n"));
        assertTrue(diagString.contains("Expected element start event for {}X12_837P"));
        assertTrue(diagString.contains("{}Unexpected"));
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().startsWith("DFDL unparse failed: "), exception.getMessage());
        assertTrue(exception.getMessage().contains("Expected element start event for {}X12_837P"), exception.getMessage());
    }

    @Test
    public void testParseErrorHandledKeepsBody() throws Exception {
        errorEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedBodiesReceived("<Unexpected />");
        mockEndpoint.expectedMessageCount(0);
        var exchange = context.createFluentProducerTemplate().to("direct:parseHandled")
                .withBody("<Unexpected />")
                .send();
        MockEndpoint.assertIsSatisfied(context);
        assertNull(exchange.getException());
        var caught = errorEndpoint.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(DfdlParseException.class, caught);
    }

    @Test
    public void testUnparseErrorHandledKeepsBody() throws Exception {
        errorEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedBodiesReceived("<Unexpected />");
        mockEndpoint.expectedMessageCount(0);
        var exchange = context.createFluentProducerTemplate().to("direct:unparseHandled")
                .withBody("<Unexpected />")
                .send();
        MockEndpoint.assertIsSatisfied(context);
        assertNull(exchange.getException());
        var caught = errorEndpoint.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(DfdlUnparseException.class, caught);
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
                from("direct:parseHandled")
                        .onException(DfdlParseException.class).handled(true).to("mock:error").end()
                        .unmarshal()
                        .dfdl("X12-837P.dfdl.xsd")
                        .to("mock:result");
                from("direct:unparseHandled")
                        .onException(DfdlUnparseException.class).handled(true).to("mock:error").end()
                        .marshal()
                        .dfdl("X12-837P.dfdl.xsd")
                        .to("mock:result");
            }
        };
    }
}
