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
package org.apache.camel.component.tika;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.universal.UniversalEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class TikaParseTest extends CamelTestSupport {

    private static final String HTML_TEXT = "Gr\u00fc\u00dfe aus K\u00f8benhavn";
    private static final byte[] HTML
            = ("<html><head><title>Test</title></head><body><p>" + HTML_TEXT + "</p></body></html>")
                    .getBytes(StandardCharsets.UTF_8);

    // ASCII only, as the html output method writes other characters as entity references
    private static final String TEXT = "Hello from Camel";
    private static final String HTML_WITH_TITLE
            = "<html><head><title>Test</title></head><body><p>" + TEXT + "</p></body></html>";
    private static final String HTML_WITHOUT_TITLE = "<html><head></head><body><p>" + TEXT + "</p></body></html>";
    private static final String HTML_WITH_EMPTY_TITLE
            = "<html><head><title></title></head><body><p>" + TEXT + "</p></body></html>";
    // NUL is not allowed in XML: Tika replaces it with U+FFFD, so the html and xml output do not fail (CAMEL-27201)
    private static final String HTML_WITH_NUL_IN_TITLE
            = "<html><head><title>Te\u0000st</title></head><body><p>" + TEXT + "</p></body></html>";

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testDocumentParse() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                String body = exchange.getIn().getBody(String.class);
                Map<String, Object> headerMap = exchange.getIn().getHeaders();
                assertThat(body, instanceOf(String.class));

                Charset detectedCharset = null;
                try (TikaInputStream bodyIs = TikaInputStream.get(new ByteArrayInputStream(body.getBytes()))) {
                    UniversalEncodingDetector encodingDetector = new UniversalEncodingDetector();
                    detectedCharset = encodingDetector.detect(bodyIs, new Metadata(), new ParseContext()).get(0).getCharset();
                } catch (IOException e1) {
                    fail();
                }

                assertThat(detectedCharset, equalTo(StandardCharsets.ISO_8859_1));
                assertThat(body, containsString("<body/>"));
                assertThat(headerMap.get(Exchange.CONTENT_TYPE), equalTo("application/x-tika-msoffice"));
                return true;
            }
        });

        File document = new File("src/test/resources/test.doc");
        template.sendBody("direct:start", document);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testDocumentParseWithEncoding() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody(String.class);
                Map<String, Object> headerMap = exchange.getIn().getHeaders();
                assertThat(body, instanceOf(String.class));

                Charset detectedCharset = null;
                try (TikaInputStream bodyIs
                        = TikaInputStream.get(
                                new ByteArrayInputStream(((String) body).getBytes(StandardCharsets.UTF_16)))) {
                    UniversalEncodingDetector encodingDetector = new UniversalEncodingDetector();
                    detectedCharset = encodingDetector.detect(bodyIs, new Metadata(), new ParseContext()).get(0).getCharset();
                } catch (IOException e1) {
                    fail();
                }

                assertThat(detectedCharset.name(), startsWith(StandardCharsets.UTF_16.name()));
                assertThat(headerMap.get(Exchange.CONTENT_TYPE), equalTo("application/vnd.oasis.opendocument.text"));
                return true;
            }
        });

        File document = new File("src/test/resources/testOpenOffice2.odt");
        template.sendBody("direct:start4", document);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testImageParse() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody(String.class);
                Map<String, Object> headerMap = exchange.getIn().getHeaders();
                assertThat(body, instanceOf(String.class));
                assertThat((String) body, containsString("<body/>"));
                assertThat(headerMap.get(Exchange.CONTENT_TYPE), equalTo("image/gif"));
                return true;
            }
        });

        File document = new File("src/test/resources/testGIF.gif");
        template.sendBody("direct:start", document);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testEmptyConfigDocumentParse() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            String body = exchange.getIn().getBody(String.class);
            // the empty parser of the config file gives no text, which the default parser gives
            assertThat(body, containsString("<body/>"));
            assertThat(body, not(containsString(HTML_TEXT)));
            return true;
        });

        template.sendBody("direct:start2", HTML);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testDefaultConfigDocumentParse() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            assertThat(exchange.getIn().getBody(String.class), containsString(HTML_TEXT));
            return true;
        });

        template.sendBody("direct:start", HTML);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testTextParseWithEncoding() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            // the exchange carries the output encoding, so the text decodes right
            assertThat(exchange.getIn().getBody(String.class), containsString(HTML_TEXT));
            assertThat(exchange.getProperty(Exchange.CHARSET_NAME), equalTo(StandardCharsets.UTF_16.name()));
            return true;
        });

        template.sendBody("direct:start5", HTML);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testHtmlParseWithTitle() {
        String html = template.requestBody("direct:html", HTML_WITH_TITLE, String.class);
        assertThat(html, containsString("<title>Test</title>"));
        assertThat(html, containsString(TEXT));
    }

    @Test
    public void testHtmlParsePlainText() {
        // plain text has no title
        String html = template.requestBody("direct:html", TEXT, String.class);
        assertEmptyTitle(html);
    }

    @Test
    public void testHtmlParsePlainTextBytes() {
        String html = template.requestBody("direct:html", TEXT.getBytes(StandardCharsets.UTF_8), String.class);
        assertEmptyTitle(html);
    }

    @Test
    public void testHtmlParseWithoutTitle() {
        String html = template.requestBody("direct:html", HTML_WITHOUT_TITLE.getBytes(StandardCharsets.UTF_8), String.class);
        assertEmptyTitle(html);
    }

    @Test
    public void testHtmlParseWithEmptyTitle() {
        String html = template.requestBody("direct:html", HTML_WITH_EMPTY_TITLE, String.class);
        assertEmptyTitle(html);
    }

    private static void assertEmptyTitle(String html) {
        // TIKA-725: browsers do not handle <title/>, so the empty title must be written with an end tag
        assertThat(html, containsString("<title></title>"));
        assertThat(html, not(containsString("<title/>")));
        assertThat(html, not(containsString("\0")));
        assertThat(html, not(containsString("&#0;")));
        assertThat(html, containsString(TEXT));
    }

    @Test
    public void testHtmlParseNulInTitle() {
        String html = template.requestBody("direct:html", HTML_WITH_NUL_IN_TITLE, String.class);
        assertThat(html, containsString("<title>Te\uFFFDst</title>"));
        assertNoNul(html);
        assertThat(html, containsString(TEXT));
    }

    @Test
    public void testXmlParseNulInTitle() {
        String xml = template.requestBody("direct:xml", HTML_WITH_NUL_IN_TITLE, String.class);
        assertThat(xml, containsString("<title>Te\uFFFDst</title>"));
        assertNoNul(xml);
        assertThat(xml, containsString(TEXT));
    }

    private static void assertNoNul(String output) {
        assertThat(output, not(containsString("\0")));
        assertThat(output, not(containsString("&#0;")));
    }

    @Test
    public void testParseBodyNotStream() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", Map.of("key", "value")));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Test
    public void testParseWithoutBody() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", null));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Test
    public void testRegistryConfigDocumentParse() throws Exception {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody(String.class);
                Map<String, Object> headerMap = exchange.getIn().getHeaders();
                assertThat(body, instanceOf(String.class));
                assertThat((String) body, containsString("<body/>"));
                assertThat(headerMap.get(Exchange.CONTENT_TYPE), equalTo("application/x-tika-msoffice"));
                return true;
            }
        });

        File document = new File("src/test/resources/test.doc");
        template.sendBody("direct:start3", document);
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("tika:parse").to("mock:result");
                from("direct:start2").to("tika:parse?tikaConfigFile=src/test/resources/tika-empty.json")
                        .to("mock:result");
                from("direct:start3").to("tika:parse?tikaLoader=#testLoader").to("mock:result");
                from("direct:start4").to("tika:parse?tikaParseOutputEncoding=" + StandardCharsets.UTF_16.name())
                        .to("mock:result");
                from("direct:html").to("tika:parse?tikaParseOutputFormat=html");
                from("direct:xml").to("tika:parse?tikaParseOutputFormat=xml");
                from("direct:start5")
                        .to("tika:parse?tikaParseOutputFormat=text&tikaParseOutputEncoding=" + StandardCharsets.UTF_16.name())
                        .to("mock:result");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("testLoader", TikaLoader.load(new File("src/test/resources/tika-empty.json").toPath()));
    }
}
