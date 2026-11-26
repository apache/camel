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
package org.apache.camel.component.file;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAMEL-18685: the charsetErrorAction option of the file consumer decides what happens with bytes the charset cannot
 * decode.
 */
public class FileConsumeCharsetErrorActionTest extends ContextTestSupport {

    // 0x81 is not mapped in windows-1252
    private static final byte[] UNMAPPABLE_WINDOWS_1252 = { 'a', (byte) 0x81, 'b' };
    // "a\u00e6b" in ISO-8859-1, which is malformed in UTF-8
    private static final byte[] MALFORMED_UTF_8 = { 'a', (byte) 0xE6, 'b' };

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testUnmappableReport() throws Exception {
        consumeAndFail(UNMAPPABLE_WINDOWS_1252, "windows-1252", "REPORT", "", UnmappableCharacterException.class);
    }

    @Test
    public void testUnmappableReplace() throws Exception {
        consume(UNMAPPABLE_WINDOWS_1252, "windows-1252", "REPLACE", "", "a\ufffdb");
    }

    @Test
    public void testUnmappableIgnore() throws Exception {
        consume(UNMAPPABLE_WINDOWS_1252, "windows-1252", "IGNORE", "", "ab");
    }

    @Test
    public void testMalformedReport() throws Exception {
        consumeAndFail(MALFORMED_UTF_8, "UTF-8", "REPORT", "", MalformedInputException.class);
    }

    @Test
    public void testMalformedReplace() throws Exception {
        consume(MALFORMED_UTF_8, "UTF-8", "REPLACE", "", "a\ufffdb");
    }

    @Test
    public void testMalformedIgnore() throws Exception {
        consume(MALFORMED_UTF_8, "UTF-8", "IGNORE", "", "ab");
    }

    @Test
    public void testDefaultIsReport() throws Exception {
        consumeAndFail(MALFORMED_UTF_8, "UTF-8", null, "", MalformedInputException.class);
    }

    @Test
    public void testCopyKeepsTheAction() {
        // the move strategies work with a copy of the file
        GenericFile<File> file = FileConsumer.asGenericFile(testDirectory().toString(),
                testFile("in/data.txt").toFile(), "windows-1252", false);
        file.setCharsetErrorAction(CharsetErrorAction.IGNORE);
        GenericFile<File> copy = new GenericFile<>();
        file.copyFrom(file, copy);
        assertEquals("windows-1252", copy.getCharset());
        assertEquals(CharsetErrorAction.IGNORE, copy.getCharsetErrorAction());
    }

    @Test
    public void testEndpointOption() {
        assertEquals(CharsetErrorAction.REPORT, context.getEndpoint(fileUri(), FileEndpoint.class).getCharsetErrorAction());
        assertEquals(CharsetErrorAction.REPLACE,
                context.getEndpoint(fileUri("?charsetErrorAction=REPLACE"), FileEndpoint.class).getCharsetErrorAction());
    }

    @Test
    public void testInputStream() throws Exception {
        writeFile(MALFORMED_UTF_8);
        // the expectations are set before the route starts, as the file is picked up at once
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("streamType", IOHelper.EncodingInputStream.class.getName());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri("UTF-8", "IGNORE", ""))
                        .process(e -> {
                            InputStream is = e.getIn().getBody(InputStream.class);
                            // the stream type that converters such as the XML converter look for is kept
                            e.getIn().setHeader("streamType", is.getClass().getName());
                            try (is) {
                                e.getIn().setBody(is.readAllBytes());
                            }
                        })
                        .to("mock:result");
            }
        });
        context.start();

        assertMockEndpointsSatisfied();

        assertEquals("ab", new String(mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class)));
    }

    @Test
    public void testInvalidValue() {
        Exception e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint(fileUri("?charsetErrorAction=IGNOR")));
        assertNotNull(ObjectHelper.getException(IllegalArgumentException.class, e), e.getMessage());
    }

    private void consume(byte[] content, String charset, String action, String options, String expected)
            throws Exception {
        writeFile(content);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(expected);
        startRoute(charset, action, options);

        assertMockEndpointsSatisfied();
        assertEquals(0, getMockEndpoint("mock:error").getReceivedCounter());
    }

    private void consumeAndFail(
            byte[] content, String charset, String action, String options, Class<? extends CharacterCodingException> type)
            throws Exception {
        writeFile(content);
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(1);
        startRoute(charset, action, options);

        assertMockEndpointsSatisfied();

        Exception cause = error.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(ObjectHelper.getException(type, cause), "Expected " + type.getSimpleName() + " but was " + cause);
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());
    }

    private void writeFile(byte[] content) throws Exception {
        Path dir = testDirectory("in", true);
        Files.write(dir.resolve("data.txt"), content);
    }

    private void startRoute(String charset, String action, String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri(charset, action, options))
                        .doTry()
                            .convertBodyTo(String.class)
                            .to("mock:result")
                        .doCatch(Exception.class)
                            .to("mock:error")
                        .end();
            }
        });
        context.start();
    }

    private String consumerUri(String charset, String action, String options) {
        return fileUri("in?initialDelay=0&delay=10&charset=" + charset
                       + (action != null ? "&charsetErrorAction=" + action : "") + options);
    }
}
