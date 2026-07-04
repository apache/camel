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
package org.apache.camel.component.langchain4j.agent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LangChain4jAgentConverterTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    @Test
    void shouldConvertLocalFile() throws Exception {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        File file = Files.writeString(tempDir.resolve("hello.txt"), "Hello file").toFile();

        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, genericFile);

        assertNotNull(body);
        assertInstanceOf(TextContent.class, body.getContent());
    }

    @Test
    void shouldConvertRemoteWrappedFileUsingFileNameHeader() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setHeader(Exchange.FILE_NAME, "photo.png");

        GenericFile<String> remoteFile = new GenericFile<>();
        remoteFile.setFile("remote-handle");
        remoteFile.setBody(new byte[] { 0x00, 0x01, 0x02 });

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, remoteFile);

        assertNotNull(body);
        assertInstanceOf(ImageContent.class, body.getContent());
    }

    @Test
    void shouldConvertRemoteWrappedFileWithMediaTypeHeader() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setHeader(Headers.MEDIA_TYPE, "text/plain");

        GenericFile<String> remoteFile = new GenericFile<>();
        remoteFile.setFile("remote-handle");
        remoteFile.setBody("Hello remote".getBytes());

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, remoteFile);

        assertNotNull(body);
        assertInstanceOf(TextContent.class, body.getContent());
    }

    @Test
    void shouldConvertRemoteWrappedFileWithInputStreamBody() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setHeader(Exchange.FILE_NAME, "notes.txt");

        GenericFile<String> remoteFile = new GenericFile<>();
        remoteFile.setFile("remote-handle");
        remoteFile.setBody(new ByteArrayInputStream("streamed content".getBytes()));

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, remoteFile);

        assertNotNull(body);
        assertInstanceOf(TextContent.class, body.getContent());
    }

    @Test
    void shouldFailForRemoteWrappedFileWithNoFileNameAndNoHeaders() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        GenericFile<String> remoteFile = new GenericFile<>();
        remoteFile.setFile("remote-handle");
        remoteFile.setBody("some data".getBytes());

        assertThrows(
                TypeConversionException.class,
                () -> context.getTypeConverter()
                        .convertTo(AiAgentBody.class, exchange, remoteFile));
    }
}
