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
package org.apache.camel.jsonpath;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A remote file (such as from the ftp consumer) is a {@link GenericFile} whose file is not a {@link java.io.File}. When
 * the consumer has a charset, the JSON must be read from the file body in that charset.
 */
public class JsonPathRemoteGenericFileCharsetTest extends CamelTestSupport {

    // stands in for org.apache.commons.net.ftp.FTPFile
    private static final class RemoteEntry {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").transform().jsonpath("$.store.book[0].title", String.class)
                        .to("mock:title");
            }
        };
    }

    @Test
    public void testRemoteFileWithCharsetUtf8() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:title");
        mock.expectedBodiesReceived("Joseph und seine Brüder");

        template.sendBody("direct:start", remoteFile("src/test/resources/germanbooks-utf8.json", "UTF-8"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testRemoteFileWithCharsetIso88591() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:title");
        mock.expectedBodiesReceived("Joseph und seine Brüder");

        template.sendBody("direct:start", remoteFile("src/test/resources/germanbooks-iso-8859-1.json", "ISO-8859-1"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testRemoteFileWithoutCharset() throws Exception {
        // control: without a charset the body is read as a stream and the encoding detected (UTF-8)
        MockEndpoint mock = getMockEndpoint("mock:title");
        mock.expectedBodiesReceived("Joseph und seine Brüder");

        template.sendBody("direct:start", remoteFile("src/test/resources/germanbooks-utf8.json", null));

        MockEndpoint.assertIsSatisfied(context);
    }

    private static GenericFile<RemoteEntry> remoteFile(String path, String charset) throws Exception {
        GenericFile<RemoteEntry> file = new GenericFile<>();
        file.setFile(new RemoteEntry());
        file.setEndpointPath("inbox");
        file.setFileName("books.json");
        file.setFileNameOnly("books.json");
        file.setCharset(charset);
        // the remote consumer has downloaded the content into the body
        file.setBody(Files.readAllBytes(Paths.get(path)));
        return file;
    }
}
