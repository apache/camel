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
package org.apache.camel.builder.xml;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.component.xslt.StreamResultHandlerFactory;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.UnitOfWorkHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class XsltBuilderTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Files.createDirectories(testDirectory());
    }

    @Test
    public void testXsltUrl() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltTransformerUrl() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = new XsltBuilder();
        builder.setTransformerURL(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltFile() throws Exception {
        File styleSheet = new File("src/test/resources/org/apache/camel/builder/xml/example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltTransformerFile() throws Exception {
        File styleSheet = new File("src/test/resources/org/apache/camel/builder/xml/example.xsl");

        XsltBuilder builder = new XsltBuilder();
        builder.setTransformerFile(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltInputStream() throws Exception {
        File styleSheet = new File("src/test/resources/org/apache/camel/builder/xml/example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(Files.newInputStream(Paths.get(styleSheet.getAbsolutePath())));

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltTransformerInputStream() throws Exception {
        File styleSheet = new File("src/test/resources/org/apache/camel/builder/xml/example.xsl");

        XsltBuilder builder = new XsltBuilder();
        builder.setTransformerInputStream(Files.newInputStream(Paths.get(styleSheet.getAbsolutePath())));

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltSource() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/builder/xml/example.xsl");
        Source styleSheet = new SAXSource(new InputSource(Files.newInputStream(Paths.get(file.getAbsolutePath()))));

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltTemplates() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/builder/xml/example.xsl");
        Source source = new SAXSource(new InputSource(Files.newInputStream(Paths.get(file.getAbsolutePath()))));

        XmlConverter converter = new XmlConverter();
        Templates styleSheet = converter.getTransformerFactory().newTemplates(source);

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltOutputString() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet).outputString();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);
        assertIsInstanceOf(String.class, exchange.getMessage().getBody());

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>", exchange.getMessage().getBody());
    }

    @Test
    public void testXsltOutputBytes() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet).outputBytes();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);
        assertIsInstanceOf(byte[].class, exchange.getMessage().getBody());

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>",
                exchange.getMessage().getBody(String.class));
    }

    @Test
    public void testXsltOutputDOM() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet).outputDOM();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);
        assertIsInstanceOf(Document.class, exchange.getMessage().getBody());

        assertEquals("<goodbye>world!</goodbye>", exchange.getMessage().getBody(String.class));
    }

    @Test
    public void testXsltOutputFile() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet).outputFile();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");
        exchange.getIn().setHeader(Exchange.XSLT_FILE_NAME, testFile("xsltout.xml").toString());

        builder.process(exchange);
        assertIsInstanceOf(File.class, exchange.getMessage().getBody());

        assertFileExists(testFile("xsltout.xml"));

        String body = exchange.getMessage().getBody(String.class);
        assertTrue(body.endsWith("<goodbye>world!</goodbye>"));
    }

    @Test
    public void testXsltOutputFileDelete() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet).outputFile().deleteOutputFile();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");
        exchange.getIn().setHeader(Exchange.XSLT_FILE_NAME, testFile("xsltout.xml").toString());

        builder.process(exchange);
        assertIsInstanceOf(File.class, exchange.getMessage().getBody());

        assertFileExists(testFile("xsltout.xml"));

        String body = exchange.getMessage().getBody(String.class);
        assertTrue(body.endsWith("<goodbye>world!</goodbye>"));

        // now done the exchange
        List<Synchronization> onCompletions = exchange.getExchangeExtension().handoverCompletions();
        UnitOfWorkHelper.doneSynchronizations(exchange, onCompletions);

        // the file should be deleted
        assertFileNotExists(testFile("xsltout.xml"));
    }

    @Test
    public void testXsltResultHandler() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);
        assertNotNull(builder.getResultHandlerFactory());
        builder.outputBytes();
        assertIsInstanceOf(StreamResultHandlerFactory.class, builder.getResultHandlerFactory());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("<hello>world!</hello>");

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>",
                exchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNullBodyDefault() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(null);

        try {
            builder.process(exchange);
            fail("Should thrown an exception");
        } catch (ExpectedBodyTypeException e) {
            // expected
        }
    }

    @Test
    public void testFailNullBody() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);
        builder.setFailOnNullBody(true);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(null);

        try {
            builder.process(exchange);
            fail("Should thrown an exception");
        } catch (ExpectedBodyTypeException e) {
            // expected
        }
    }

    @Test
    public void testNotFailNullBody() throws Exception {
        URL styleSheet = getClass().getResource("example.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet);
        builder.setFailOnNullBody(false);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(null);

        builder.process(exchange);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye/>", exchange.getMessage().getBody(String.class));
    }

}
