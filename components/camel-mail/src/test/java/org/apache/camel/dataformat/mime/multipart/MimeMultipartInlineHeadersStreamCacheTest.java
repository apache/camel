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
package org.apache.camel.dataformat.mime.multipart;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * With {@code headersInline=true} the unmarshal parses the MIME headers from the body stream. When the body is an
 * in-memory stream cache, the MIME header parser marks that stream after every CR of the headers, so a later reset of
 * the stream cache must still go back to the start of the body.
 */
class MimeMultipartInlineHeadersStreamCacheTest extends CamelTestSupport {

    private static final String MULTIPART = "Content-Type: multipart/mixed; boundary=\"XYZ\"\r\n"
                                            + "MIME-Version: 1.0\r\n"
                                            + "X-Order: 42\r\n"
                                            + "\r\n"
                                            + "--XYZ\r\n"
                                            + "Content-Type: text/plain\r\n"
                                            + "\r\n"
                                            + "Body text\r\n"
                                            + "--XYZ\r\n"
                                            + "Content-Type: text/plain\r\n"
                                            + "Content-Disposition: attachment; filename=\"a.txt\"\r\n"
                                            + "\r\n"
                                            + "Attachment text\r\n"
                                            + "--XYZ--\r\n";

    private static final String NOT_MULTIPART = "first line\r\n"
                                                + "second line\r\n"
                                                + "\r\n"
                                                + "third line\r\n";

    private static InputStream stream(String s) {
        // not a ByteArrayInputStream, so that stream caching copies it into an in-memory stream cache, as for the
        // body of an HTTP request
        return new FilterInputStream(new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII))) {
        };
    }

    @Test
    void originalMessageOfAFailedExchangeHasTheWholeBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:fail", stream(MULTIPART));

        dead.assertIsSatisfied();
        assertEquals(MULTIPART, dead.getReceivedExchanges().get(0).getMessage().getBody(String.class));
    }

    @Test
    void bodyThatIsNotMultipartIsKeptWhole() {
        Exchange out = template.send("direct:unmarshal", e -> e.getMessage().setBody(stream(NOT_MULTIPART)));

        assertEquals(NOT_MULTIPART, out.getMessage().getBody(String.class));
    }

    @Test
    void streamCacheKeptInAPropertyCanBeReadAgain() {
        Exchange out = template.send("direct:keep", e -> e.getMessage().setBody(stream(MULTIPART)));

        StreamCache kept = assertInstanceOf(StreamCache.class, out.getProperty("original"));
        kept.reset();
        assertEquals(MULTIPART, out.getContext().getTypeConverter().convertTo(String.class, out, kept));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setStreamCaching(true);

                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage());

                from("direct:fail")
                        .unmarshal().mimeMultipart(false, true, false)
                        .throwException(new IllegalStateException("downstream failure"));

                from("direct:unmarshal")
                        .unmarshal().mimeMultipart(false, true, false);

                from("direct:keep")
                        .setProperty("original", body())
                        .unmarshal().mimeMultipart(false, true, false);
            }
        };
    }
}
