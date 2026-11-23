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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FTP client compacts the directory of a stepwise upload before it changes into each of its segments, and gives no
 * special meaning to a scheme or a drive at the start of it. Verifies that a name that the upload would resolve outside
 * the starting directory is refused.
 */
public class FtpProducerJailStartingDirectoryResolvedNameIT extends FtpServerTestSupport {

    private String ftpUrl(String directory) {
        return "ftp://admin@localhost:{{ftp.server.port}}/" + directory + "?password=admin";
    }

    private void assertRefused(String directory, String name) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(ftpUrl(directory), "Hello World", Exchange.FILE_NAME, name));
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(iae.getMessage().contains("as the filename is jailed to the starting directory"), iae.getMessage());
    }

    @Test
    public void testPrefixedNameEscapingTheSessionRootIsRefused() {
        assertRefused("", "http:/../../planted.txt");
        assertRefused("", "a:/../../planted.txt");

        assertFalse(service.ftpFile("planted.txt").toFile().exists());
    }

    @Test
    public void testNameInsideTheStartingDirectoryIsWritten() {
        template.sendBodyAndHeader(ftpUrl("upload/jail"), "Hello World", Exchange.FILE_NAME, "sub/../hello.txt");

        assertTrue(service.ftpFile("upload/jail/hello.txt").toFile().exists());
    }
}
