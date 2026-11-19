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
package org.apache.camel.component.file.remote.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ftp producer refuses a file or directory name with a carriage return or a line feed, as the FTP control
 * connection sends one command per line, and the server gets no file or directory for it.
 */
public class FtpProducerFileNameLineBreakIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/upload/linebreak?password=admin";
    }

    @Test
    public void testFileNameWithLineBreakIsRefused() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, "before\r\nafter.txt"));
        GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().contains("the character U+000D"), cause.getMessage());

        assertEquals(List.of(), serverEntriesNamed("before", "after"));
    }

    @Test
    public void testDirectoryNameWithLineBreakIsRefused() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, "before\nafter/hello.txt"));
        assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());

        assertEquals(List.of(), serverEntriesNamed("before", "after"));
    }

    @Test
    public void testPlainFileNameIsStored() throws Exception {
        template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertEquals("Hello World", Files.readString(service.ftpFile("upload/linebreak/hello.txt")));
    }

    private List<Path> serverEntriesNamed(String... parts) throws Exception {
        try (Stream<Path> entries = Files.walk(service.getFtpRootDir())) {
            return entries.filter(p -> Stream.of(parts).anyMatch(part -> p.getFileName().toString().contains(part)))
                    .toList();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(getFtpUrl());
            }
        };
    }
}
