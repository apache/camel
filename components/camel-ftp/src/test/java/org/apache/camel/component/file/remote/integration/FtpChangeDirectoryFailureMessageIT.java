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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failed change of directory gives the reply of the server and the current directory, that a relative path starts
 * from. The home directory of the user is the root of the server.
 */
class FtpChangeDirectoryFailureMessageIT extends FtpServerTestSupport {

    private Exchange send(String directory) {
        return template.send("ftp://admin@localhost:{{ftp.server.port}}/" + directory + "?password=admin&autoCreate=false",
                e -> {
                    e.getIn().setBody("Hello World");
                    e.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
                });
    }

    @Test
    void changeDirectoryFailureGivesTheReplyAndTheCurrentDirectory() {
        Exchange out = send("missing");

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        // the reply to the change of directory, not to the command that reads the current directory
        assertEquals(550, e.getCode());
        assertTrue(e.getReason().startsWith("550 "), e.getReason());
        assertTrue(e.getMessage().endsWith(
                " Cannot change directory to: missing. The path is relative to the current directory: /. Code: 550"),
                e.getMessage());
    }

    @Test
    void changeDirectoryFailureGivesTheDirectoryOfThePreviousSegment() throws Exception {
        Files.createDirectories(service.getFtpRootDir().resolve("existing"));

        Exchange out = send("existing/missing");

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        assertEquals(550, e.getCode());
        assertTrue(e.getMessage().endsWith(
                " Cannot change directory to: missing. The path is relative to the current directory: /existing. Code: 550"),
                e.getMessage());
    }

    @Test
    void existingDirectoryUploads() throws Exception {
        Files.createDirectories(service.getFtpRootDir().resolve("existing"));

        Exchange out = send("existing");

        assertNull(out.getException());
        assertTrue(Files.exists(service.getFtpRootDir().resolve("existing/hello.txt")));
    }

    @Test
    void storeOfABodyThatIsNotFileContentSaysWhy() throws Exception {
        // a List, as unmarshal: csv leaves it, to a directory that exists: the body is not file content (CAMEL-27151)
        Files.createDirectories(service.getFtpRootDir().resolve("failed"));
        Exchange out = template.send("ftp://admin@localhost:{{ftp.server.port}}/failed?password=admin", e -> {
            e.getIn().setBody(List.of(List.of("INV-3006", "12.x")));
            e.getIn().setHeader(Exchange.FILE_NAME, "invoices.csv");
        });

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        assertTrue(e.getMessage().startsWith("Cannot store file: "), e.getMessage());
        assertTrue(e.getMessage().contains("the body is a List, which is not file content: marshal it first (marshal: csv,"
                                           + " for the file name invoices.csv)"),
                e.getMessage());
        assertFalse(Files.exists(service.getFtpRootDir().resolve("failed/invoices.csv")));
    }
}
